package cn.sijay.oi.file.controller;

import cn.sijay.oi.file.service.FileService;
import cn.sijay.oi.support.AuthenticatedIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件管理模块集成测试，覆盖统一文件服务的全场景行为。
 *
 * <p>覆盖场景（与文件管理需求一一对应）：
 * <ol>
 *   <li>上传成功：返回文件唯一 id 与带签名的临时访问地址，凭地址可公开取流（免登录）；</li>
 *   <li>上传校验：空文件（606001）、扩展名不在白名单（606002）、大小超限（606003）、未登录（401）；</li>
 *   <li>地址管理：/file/{id}/url 重新签发有效；签名被篡改/缺失/过期返回 606005；</li>
 *   <li>查询与删除：元数据查询、分页条件查询、删除后原地址失效且重复删除 606004。</li>
 * </ol>
 *
 * <p>登录态：继承 {@link AuthenticatedIntegrationTest}；默认请求携带超管令牌，
 * "无令牌"场景使用 {@link #rawMockMvc}。
 */
@DisplayName("文件管理模块集成测试（统一上传 + 签名访问 + 查询删除）")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:file;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        // 存储指向构建产物目录，避免测试文件污染工作目录
        "file.upload.storage-path=target/test-uploads",
        // 收紧大小上限以便测试业务级超限校验
        "file.upload.max-size=1KB"
})
class FileControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private FileService fileService;

    /**
     * PNG 图片字节（1x1 透明 PNG 的文件头签名）。
     */
    private static final byte[] PNG_BYTES = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

    @Test
    @DisplayName("上传成功：返回文件 id 与签名访问地址，凭地址公开取流（免登录）")
    void uploadSuccessAndPublicAccess() throws Exception {
        UploadResult uploaded = upload("demo.png", "image/png", "avatar");
        // 业务侧只持久化文件 id；访问地址为带签名的公开端点
        org.junit.jupiter.api.Assertions.assertTrue(
                uploaded.accessUrl().startsWith("/file/access/" + uploaded.fileId() + "?expires="));

        // 公开访问端点：免登录（rawMockMvc 无令牌）凭有效签名取流，内容与响应头一致
        rawMockMvc.perform(get(uploaded.accessUrl()))
                  .andExpect(status().isOk())
                  .andExpect(content().contentType("image/png"))
                  .andExpect(content().bytes(PNG_BYTES))
                  .andExpect(MockMvcResultMatchers.header()
                                          .string("Content-Disposition", containsString("demo.png")));
    }

    @Test
    @DisplayName("上传校验：空文件 606001、非法扩展名 606002、无扩展名 606002")
    void uploadValidation() throws Exception {
        // 空文件 → 606001
        mockMvc.perform(multipart("/file/upload")
                                .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                                .param("businessKey", "avatar"))
               .andExpect(jsonPath("$.code").value(606001))
               .andExpect(jsonPath("$.message").value("上传文件不能为空"));

        // 扩展名不在全局白名单（.exe）→ 606002
        mockMvc.perform(multipart("/file/upload")
                                .file(new MockMultipartFile("file", "setup.exe", "application/octet-stream", PNG_BYTES)))
               .andExpect(jsonPath("$.code").value(606002))
               .andExpect(jsonPath("$.message").value("不支持的文件类型"));

        // 无扩展名 → 606002
        mockMvc.perform(multipart("/file/upload")
                                .file(new MockMultipartFile("file", "noext", "application/octet-stream", PNG_BYTES)))
               .andExpect(jsonPath("$.code").value(606002));
    }

    @Test
    @DisplayName("大小限制：超过配置上限返回 606003")
    void uploadSizeExceeded() throws Exception {
        // 测试上下文配置 file.upload.max-size=1KB，上传 2KB 文本 → 606003
        byte[] bigBytes = new byte[2048];
        mockMvc.perform(multipart("/file/upload")
                                .file(new MockMultipartFile("file", "big.txt", "text/plain", bigBytes)))
               .andExpect(jsonPath("$.code").value(606003))
               .andExpect(jsonPath("$.message").value("文件大小超出限制"));
    }

    @Test
    @DisplayName("未登录访问：上传接口返回 401")
    void uploadRequiresLogin() throws Exception {
        rawMockMvc.perform(multipart("/file/upload")
                                   .file(new MockMultipartFile("file", "demo.png", "image/png", PNG_BYTES)))
                  .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("地址管理：重新签发可用；签名篡改/缺失/过期返回 606005")
    void accessUrlManagement() throws Exception {
        UploadResult uploaded = upload("avatar.jpg", "image/jpeg", "avatar");

        // 重新签发：新地址凭签名可访问
        String body = mockMvc.perform(get("/file/" + uploaded.fileId() + "/url"))
                             .andExpect(jsonPath("$.code").value(200))
                             .andReturn().getResponse().getContentAsString();
        Object urlValue = JsonPath.read(body, "$.data");
        String newUrl = String.valueOf(urlValue);
        org.junit.jupiter.api.Assertions.assertTrue(newUrl.startsWith("/file/access/" + uploaded.fileId() + "?expires="));
        mockMvc.perform(get(newUrl))
               .andExpect(status().isOk());

        // 签名被篡改 → 606005
        mockMvc.perform(get("/file/access/" + uploaded.fileId() + "?expires=9999999999&sign=deadbeef"))
               .andExpect(jsonPath("$.code").value(606005))
               .andExpect(jsonPath("$.message").value("文件访问链接已失效"));

        // 缺失签名参数 → 400
        mockMvc.perform(get("/file/access/" + uploaded.fileId() + "?expires=9999999999"))
               .andExpect(jsonPath("$.code").value(400));

        // 已过期地址（服务层签发负 TTL）→ 606005
        String expired = fileService.issueAccessUrl(uploaded.fileId(), Duration.ofSeconds(-60));
        mockMvc.perform(get(expired))
               .andExpect(jsonPath("$.code").value(606005));
    }

    @Test
    @DisplayName("查询与删除：元数据回显、分页条件查询、删除后原地址失效且重复删除 606004")
    void getPageAndDelete() throws Exception {
        UploadResult uploaded = upload("查询文件.pdf", "application/pdf", "doc-test");

        // 元数据查询：字段回显 + 访问地址下发
        mockMvc.perform(get("/file/" + uploaded.fileId()))
               .andExpect(jsonPath("$.code").value(200))
               .andExpect(jsonPath("$.data.originalName").value("查询文件.pdf"))
               .andExpect(jsonPath("$.data.businessKey").value("doc-test"))
               .andExpect(jsonPath("$.data.extension").value("pdf"))
               .andExpect(jsonPath("$.data.accessUrl").isNotEmpty());

        // 分页条件查询：businessKey 精确过滤（total 为 Long，经全局 Jackson 配置序列化为字符串）
        mockMvc.perform(get("/file/page").param("businessKey", "doc-test").param("pageNum", "1").param("pageSize", "10"))
               .andExpect(jsonPath("$.code").value(200))
               .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
               .andExpect(jsonPath("$.total").value("1"));

        // 删除前签发一个合法地址；删除后该地址签名仍有效但文件已删 → 606004
        String urlBeforeDelete = fileService.issueAccessUrl(uploaded.fileId());
        mockMvc.perform(delete("/file/" + uploaded.fileId()))
               .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/file/" + uploaded.fileId()))
               .andExpect(jsonPath("$.code").value(606004))
               .andExpect(jsonPath("$.message").value("文件不存在或已删除"));
        mockMvc.perform(get(urlBeforeDelete))
               .andExpect(jsonPath("$.code").value(606004));

        // 重复删除 → 606004
        mockMvc.perform(delete("/file/" + uploaded.fileId()))
               .andExpect(jsonPath("$.code").value(606004));
    }

    /**
     * 上传并从响应 JSON 提取文件 id 与访问地址。
     *
     * <p>JsonPath 陷阱：先赋值 Object 再转 String——直接
     * {@code String.valueOf(JsonPath.read(...))} 会命中 valueOf(char[]) 重载。
     */
    private UploadResult upload(String fileName, String contentType, String businessKey) throws Exception {
        String body = mockMvc.perform(multipart("/file/upload")
                                              .file(new MockMultipartFile("file", fileName, contentType, PNG_BYTES))
                                              .param("businessKey", businessKey))
                             .andExpect(jsonPath("$.code").value(200))
                             .andReturn().getResponse().getContentAsString();
        Object idValue = JsonPath.read(body, "$.data.id");
        Long fileId = Long.valueOf(String.valueOf(idValue));
        Object urlValue = JsonPath.read(body, "$.data.accessUrl");
        return new UploadResult(fileId, String.valueOf(urlValue));
    }

    /**
     * 上传结果载体（文件 id + 临时访问地址）。
     */
    private record UploadResult(Long fileId, String accessUrl) {
    }
}

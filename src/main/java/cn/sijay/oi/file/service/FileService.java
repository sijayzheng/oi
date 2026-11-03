package cn.sijay.oi.file.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.dev33.satoken.stp.StpUtil;
import cn.sijay.oi.file.entity.SysFile;
import cn.sijay.oi.file.model.SysFileQuery;
import cn.sijay.oi.file.model.SysFileVO;
import cn.sijay.oi.file.repository.SysFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * 统一文件管理服务，系统内所有文件上传、存储、查询、删除的唯一入口。
 *
 * <p><b>调用规范</b>（全系统强制约定）：
 * <ol>
 *   <li>业务场景上传文件时，禁止自行处理文件实体或存储路径，仅调用本服务完成上传，
 *       上传成功后<b>只接收并持久化返回的文件唯一 id</b>（不落 URL、不落路径）；</li>
 *   <li>业务模块需要访问文件时，仅凭存储的文件 id 调用 {@link #issueAccessUrl} 获取
 *       带签名与有效期的临时访问地址，禁止直接操作文件存储介质；</li>
 *   <li>删除文件仅经 {@link #delete}（软删记录 + 清理物理文件）。</li>
 * </ol>
 *
 * <p><b>基础能力</b>：扩展名白名单校验（全局默认 + 业务自定义两级）、文件大小限制、
 * UUID 唯一文件标识、yyyy/MM 月份分桶存储路径映射、HMAC-SHA256 签名访问地址与有效期管理。
 *
 * @author sijay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /**
     * 存储根目录（应用工作目录相对路径）；文件按 yyyy/MM 月份分桶落盘。
     */
    private static final Path DEFAULT_STORAGE_ROOT = Path.of("uploads");

    /**
     * 月份分桶目录格式；存储路径映射规则 = 根目录/yyyy/MM/uuid.ext。
     */
    private static final DateTimeFormatter MONTH_BUCKET = DateTimeFormatter.ofPattern("yyyy/MM");

    /**
     * 图片类扩展名白名单；头像等纯图片业务场景的自定义约束
     * （在全局默认白名单之上进一步收窄）。
     */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * HMAC-SHA256 算法名；访问地址签名算法。
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SysFileRepository fileRepository;

    /**
     * 存储根目录（应用工作目录相对路径）；application.yaml 可配置，测试可指向临时目录隔离。
     */
    @Value("${file.upload.storage-path:uploads}")
    private String storagePath;

    /**
     * 全局默认扩展名白名单（小写、不含点）；application.yaml 可配置。
     */
    @Value("${file.upload.allowed-extensions:jpg,jpeg,png,gif,webp,pdf,txt,doc,docx,xls,xlsx,ppt,pptx,zip,rar,7z}")
    private Set<String> allowedExtensions;

    /**
     * 单文件大小上限；application.yaml 可配置（Spring 自动解析 10MB 等写法）。
     */
    @Value("${file.upload.max-size:10MB}")
    private DataSize maxSize;

    /**
     * 访问地址签名密钥；生产环境务必更换为随机高熵值。
     */
    @Value("${file.access.secret:oi-file-access-secret}")
    private String accessSecret;

    /**
     * 访问地址默认有效期（分钟）。
     */
    @Value("${file.access.ttl-minutes:30}")
    private long accessTtlMinutes;

    /**
     * 上传文件（全局默认白名单校验）。
     *
     * <p>流程：空文件校验 → 扩展名白名单校验 → 大小限制校验 → UUID 唯一标识 +
     * 月份分桶落盘 → 元数据落库 → 返回文件 id 与临时访问地址。
     *
     * @param file        上传的文件（multipart）
     * @param businessKey 业务标识；标记文件归属场景（如 avatar），可为空
     * @return 文件信息（含唯一 id 与临时访问地址）
     * @throws BusinessException 文件为空（606001）、类型不支持（606002）、
     *                           大小超限（606003）、落盘失败（606006）
     */
    public SysFileVO upload(MultipartFile file, String businessKey) {
        return upload(file, businessKey, allowedExtensions);
    }

    /**
     * 上传图片文件（图片扩展名白名单校验）。
     *
     * <p>使用场景：头像等纯图片业务；在统一上传流程之上进一步收窄类型约束。
     *
     * @param file        上传的图片文件（multipart）
     * @param businessKey 业务标识
     * @return 文件信息（含唯一 id 与临时访问地址）
     * @throws BusinessException 文件为空（606001）、类型不支持（606002）、
     *                           大小超限（606003）、落盘失败（606006）
     */
    public SysFileVO uploadImage(MultipartFile file, String businessKey) {
        return upload(file, businessKey, IMAGE_EXTENSIONS);
    }

    /**
     * 上传核心流程（统一入口，白名单由调用方决定）。
     */
    private SysFileVO upload(MultipartFile file, String businessKey, Set<String> whitelist) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.FILE_EMPTY);
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extractExtension(originalName);
        if (extension.isEmpty() || !whitelist.contains(extension)) {
            throw new BusinessException(BusinessErrorCode.FILE_EXTENSION_NOT_ALLOWED);
        }
        if (file.getSize() > maxSize.toBytes()) {
            throw new BusinessException(BusinessErrorCode.FILE_SIZE_EXCEEDED);
        }

        // 唯一文件标识 + 月份分桶路径映射（规则收敛于本服务，对外不暴露）
        String storageName = java.util.UUID.randomUUID() + "." + extension;
        String bucket = LocalDateTime.now().format(MONTH_BUCKET);
        Path target = storageRoot().resolve(bucket).resolve(storageName).toAbsolutePath();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        }
        catch (IOException e) {
            log.error("[FileService] 文件落盘失败: {}", target, e);
            throw new BusinessException(BusinessErrorCode.FILE_UPLOAD_FAILED);
        }

        SysFile entity = new SysFile();
        entity.setOriginalName(originalName);
        entity.setStorageName(storageName);
        entity.setExtension(extension);
        entity.setContentType(file.getContentType());
        entity.setSize(file.getSize());
        entity.setBusinessKey(businessKey);
        entity.setCreateBy(currentUserId());
        SysFile saved = fileRepository.save(entity);
        log.info("[FileService] 文件上传成功: id={}, name={}, size={}, business={}",
                saved.getId(), originalName, saved.getSize(), businessKey);

        SysFileVO vo = SysFileVO.from(saved);
        vo.setAccessUrl(issueAccessUrl(saved.getId()));
        return vo;
    }

    /**
     * 按文件 id 查询文件元数据。
     *
     * @param id 文件 id
     * @return 文件信息（accessUrl 为空，需要访问地址时调用 {@link #issueAccessUrl}）
     * @throws BusinessException 文件不存在或已删除（606004）
     */
    @Transactional(readOnly = true)
    public SysFileVO getById(Long id) {
        SysFile entity = getEntity(id);
        SysFileVO vo = SysFileVO.from(entity);
        vo.setAccessUrl(issueAccessUrl(entity.getId()));
        return vo;
    }

    /**
     * 分页条件查询文件列表（管理端检索）。
     *
     * <p>实现说明：Query-by-Example——originalName 走 {@code CONTAINING} 模糊匹配
     * （忽略大小写），businessKey/extension 精确匹配，未设置的字段自动忽略；
     * 软删行由 {@code @SQLRestriction} 全局过滤。
     *
     * @param query 业务过滤条件
     * @param page  分页与排序参数（构造期已归一化，此处仅校验排序字段安全性）
     * @return 分页结果（accessUrl 为空）
     */
    @Transactional(readOnly = true)
    public Page<SysFileVO> page(SysFileQuery query, PageQuery page) {
        page.validate();
        SysFile probe = new SysFile();
        probe.setOriginalName(normalizeBlank(query.getOriginalName()));
        probe.setBusinessKey(normalizeBlank(query.getBusinessKey()));
        probe.setExtension(normalizeBlank(query.getExtension()));
        ExampleMatcher matcher = ExampleMatcher.matching()
                                               .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
                                               .withIgnoreCase()
                                               .withIgnorePaths("id", "storageName", "size", "createBy",
                                                       "createTime", "updateBy", "updateTime", "deleted");
        PageRequest pageRequest = page.toPageRequest();
        return fileRepository.findAll(Example.of(probe, matcher), pageRequest)
                             .map(SysFileVO::from);
    }

    /**
     * 为文件签发带有效期的临时访问地址。
     *
     * <p>地址格式：{@code /file/access/{id}?expires={epoch秒}&sign={HMAC-SHA256(id:expires)}}；
     * 签名密钥来自配置，过期时间（epoch 秒）参与签名防篡改，公开端点
     * {@code GET /file/access/{id}} 校验通过后输出文件流。
     *
     * @param id 文件 id
     * @return 临时访问地址（相对路径）；默认有效期为配置的 ttl-minutes
     * @throws BusinessException 文件不存在或已删除（606004）
     */
    public String issueAccessUrl(Long id) {
        return issueAccessUrl(id, java.time.Duration.ofMinutes(accessTtlMinutes));
    }

    /**
     * 为文件签发指定有效期的临时访问地址（有效期管理入口）。
     *
     * @param id  文件 id
     * @param ttl 有效期；负值表示立即过期（测试场景）
     * @return 临时访问地址（相对路径）
     * @throws BusinessException 文件不存在或已删除（606004）
     */
    public String issueAccessUrl(Long id, java.time.Duration ttl) {
        getEntity(id);
        long expires = Instant.now().plus(ttl).getEpochSecond();
        return "/file/access/" + id + "?expires=" + expires + "&sign=" + sign(id, expires);
    }

    /**
     * 删除文件：软删记录 + 尽力清理物理文件。
     *
     * <p>实现说明：先软删（幂等，重复删除返回 606004），再按存储路径映射规则
     * 定位物理文件并删除；物理清理失败不阻断流程（仅记录日志，避免脏数据残留
     * 导致业务无法收尾）。注意：删除后所有引用该文件 id 的业务数据将无法再获取
     * 访问地址，调用方须自行评估引用关系（见调用规范文档）。
     *
     * @param id 文件 id
     * @throws BusinessException 文件不存在或已删除（606004）
     */
    @Transactional
    public void delete(Long id) {
        SysFile entity = getEntity(id);
        if (fileRepository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.FILE_NOT_FOUND);
        }
        String bucket = entity.getCreateTime().format(MONTH_BUCKET);
        Path physical = storageRoot().resolve(bucket).resolve(entity.getStorageName()).toAbsolutePath();
        try {
            Files.deleteIfExists(physical);
        }
        catch (IOException e) {
            log.warn("[FileService] 物理文件清理失败（记录已软删）: {}", physical, e);
        }
        log.info("[FileService] 文件删除成功: id={}, business={}", id, entity.getBusinessKey());
    }

    /**
     * 校验访问签名并返回文件实体（公开访问端点的校验入口）。
     *
     * <p>校验顺序：有效期（expires 未过期）→ 签名一致性（防篡改）→ 文件存在性；
     * 任一失败统一抛 606005（签名失效）或 606004（文件不存在），
     * 不区分具体失败原因，防止签名机制被探测。
     *
     * @param id      文件 id
     * @param expires 过期时间（epoch 秒）
     * @param sign    请求携带的签名
     * @return 文件实体（未软删）
     * @throws BusinessException 签名无效或已过期（606005）、文件不存在（606004）
     */
    public SysFile validateAccess(Long id, long expires, String sign) {
        if (sign == null || expires < Instant.now().getEpochSecond()
                || !constantTimeEquals(sign(id, expires), sign)) {
            throw new BusinessException(BusinessErrorCode.FILE_SIGNATURE_INVALID);
        }
        return getEntity(id);
    }

    /**
     * 解析物理文件路径（存储路径映射的唯一出口，供流式输出使用）。
     *
     * @param entity 文件实体
     * @return 物理文件绝对路径
     */
    public Path resolvePhysicalPath(SysFile entity) {
        String bucket = entity.getCreateTime().format(MONTH_BUCKET);
        return storageRoot().resolve(bucket).resolve(entity.getStorageName()).toAbsolutePath();
    }

    /**
     * 存储根目录；未配置时回退默认 uploads 目录。
     */
    private Path storageRoot() {
        return storagePath == null || storagePath.isBlank()
                ? DEFAULT_STORAGE_ROOT
                : Path.of(storagePath);
    }

    /**
     * 按 id 获取未软删的文件实体。
     */
    private SysFile getEntity(Long id) {
        return fileRepository.findById(id)
                             .orElseThrow(() -> new BusinessException(BusinessErrorCode.FILE_NOT_FOUND));
    }

    /**
     * 计算访问签名：HMAC-SHA256(secret, "{id}:{expires}") 的小写十六进制串。
     */
    private String sign(Long id, long expires) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(accessSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((id + ":" + expires).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (Exception e) {
            throw new IllegalStateException("文件访问签名计算失败", e);
        }
    }

    /**
     * 常量时间字符串比较；防时序攻击的签名比对。
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 提取并归一化扩展名：小写、不含点；无扩展名返回空串。
     */
    private String extractExtension(String originalName) {
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 当前登录人 id；匿名上下文（系统内部调用）返回 0。
     */
    private Long currentUserId() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? 0L : Long.valueOf(loginId.toString());
        }
        catch (RuntimeException e) {
            // sa-token 上下文未激活（非 Web 线程调用）等场景：按系统操作人处理
            return 0L;
        }
    }

    /**
     * 空字符串过滤条件归一化为 null（防 CONTAINING 空串误过滤 NULL 行，见 SysUserService 同名方法）。
     */
    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

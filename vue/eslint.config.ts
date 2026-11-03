import antfu from '@antfu/eslint-config'
import pluginOxlint from 'eslint-plugin-oxlint'

export default antfu(
  {
    stylistic: {
      indent: 2,
      quotes: 'single',
      semi: false,
      jsx: true,
    },
    vue: true,
    typescript: true,
    formatters: true,
    ignores: [
      '**/dist/**',
      '**/dist-ssr/**',
      '**/coverage/**',
      'node_modules/**',
    ],
    rules: {
      'vue/block-order': [
        'error',
        {
          order: ['template', 'script', 'style'],
        },
      ],
      'style/brace-style': [
        'error',
        '1tbs',
        { allowSingleLine: true },
      ],
      'no-undef': 'off',
      'unused-imports/no-unused-vars': 'off',
      'no-unused-vars': [
        'warn',
        {
          args: 'none',
        },
      ],
      'ts/consistent-type-definitions': ['error', 'interface'],
      '@typescript-eslint/promise-function-async': 'off',
      'ts/strict-boolean-expressions': 'off',
    },
  },
  ...pluginOxlint.buildFromOxlintConfigFile('.oxlintrc.json'),
)

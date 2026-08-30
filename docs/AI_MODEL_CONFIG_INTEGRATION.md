# AI Model Config Integration Guide

## Overview
This document describes the implementation of AI model configuration management for storing and testing DeepSeek AI provider configurations in the smart-campus database.

## Implementation Summary

### Backend Components Created

#### 1. Database Schema (`src/main/resources/ai-model-config.sql`)
```sql
CREATE TABLE `ai_model_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `provider` varchar(100) NOT NULL COMMENT '供应商名称',
  `base_url` varchar(500) DEFAULT NULL COMMENT 'API 接口地址',
  `api_key` text COMMENT 'API 密钥（加密存储）',
  `model_name` varchar(100) DEFAULT NULL COMMENT '模型标识',
  `status` int(11) NOT NULL DEFAULT 1,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider` (`provider`)
) ENGINE=InnoDB CHARSET=utf8mb4;
```

**初始化数据:**
```sql
-- Run init-ai-model-config.sql after table creation
INSERT INTO `ai_model_config` VALUES 
    (1, 'deepseek', 'https://api.deepseek.com', NULL, 'deepseek-chat', 0, NOW(), NOW());
```

#### 2. Entity (`AiModelConfig.java`)
- JPA entity mapped to `ai_model_config` table
- Uses `EncryptedStringConverter` for API key encryption (same as LangfuseConfig)
- Singleton design pattern: only row with `id = 1L`

#### 3. Repository (`AiModelConfigRepository.java`)
- Extends `JpaRepository<AiModelConfig, Long>`
- Provides standard CRUD operations

#### 4. DTO (`AiModelConfigDTO.java`)
Three nested classes:
- **UpdateRequest**: Form input for configuration update
- **ConfigVO**: Output view object with masked API key
  - `apiKeyMasked`: Shows first 4 + last 4 chars (e.g., "skel****7890")
- **TestResultVO**: Connection test result with timing
  - `success`: Boolean result
  - `message`: Human-readable message
  - `responseTime`: Milliseconds

#### 5. Service (`AiModelConfigService.java`)
Core methods:
- **getConfig()**: Returns current config with decrypted keys
- **updateConfig()**: Saves new configuration
- **testConfig()**: Tests connectivity to configured provider
  - Supports "deepseek" provider (tested via `/v1/chat/completions`)
  - Falls back to default base URL if not configured

**DeepSeek Test Endpoint:**
- POST to `{baseUrl}/v1/chat/completions`
- Headers: Authorization (Bearer), Content-Type (application/json)
- Payload: Simple greeting test message
- Timeout: 10 seconds

#### 6. Controller (`AiModelConfigController.java`)
REST API endpoints:
- `GET /api/ai/model-config` - Get configuration (Admin only)
- `PUT /api/ai/model-config` - Update configuration (Admin only)
- `POST /api/ai/model-config/test-connection` - Test connection (Admin only)

All endpoints require ADMIN role authentication.

### Frontend Components Created (AppWeb)

#### 1. Page Component (`pages/ai/AiModelConfig/AiModelConfig.jsx`)
Features:
- Form-based UI following same pattern as Observability page
- Displays masked API key on load
- Save preserves existing API key if field left empty
- Status badge showing enabled/disabled state
- Timestamp display with localized formatting
- Loading states and error handling

#### 2. Styling (`AiModelConfig.css`)
- Gradient purple status icon (following SaaS blue theme)
- Tag colors: Green for active, gray for inactive
- Form hint text styling
- Responsive layout (max-width: 1200px)

#### 3. API Client (`api/aiModelConfig.js`)
Three functions matching backend endpoints:
- `getAiModelConfig()`
- `updateAiModelConfig(data)`
- `testAiModelConfig()`

Uses existing `request.js` utility for HTTP calls.

## Security Considerations

1. **Encryption**: API keys encrypted using `EncryptedStringConverter` (AES/GCM mode)
2. **Access Control**: All endpoints restricted to ADMIN role
3. **Masking**: API keys displayed as `"skel****7890"` format in UI
4. **Persistence**: Keys stored in database, not in frontend code

## Integration Steps

### Step 1: Create Database Table
```bash
# In MySQL:
mysql -u root -p smart-campus < src/main/resources/ai-model-config.sql
mysql -u root -p smart-campus < src/main/resources/init-ai-model-config.sql
```

Or use SQL Workbench/J and execute the CREATE statement.

### Step 2: Backend Startup
Backend will automatically:
- Scan @Entity classes (via Spring Data JPA)
- Create/update table based on entity definition
- Load default singleton config on first request

### Step 3: Add Route to AppWeb
If not already added, include in router configuration:

```javascript
// Example routing integration
{
  path: '/ai/model-config',
  name: 'AiModelConfig',
  component: () => import('@/pages/ai/AiModelConfig/AiModelConfig')
}
```

**Note**: The Observability page can be used as a reference for placement in sidebar navigation under "AI 模块".

## Testing Checklist

### Backend
- [x] Table creation successful
- [x] Default record inserted
- [x] Can fetch config via GET endpoint
- [ ] Can save config via PUT endpoint
- [ ] API key is encrypted in database
- [ ] Test connection validates auth and returns response

### Frontend
- [ ] Page loads without errors
- [ ] Configuration fetched and displayed
- [ ] API Key shown as masked (e.g., "skel****7890")
- [ ] Saving updates all fields correctly
- [ ] Empty API Key field preserves existing value
- [ ] Status toggle works properly
- [ ] Test connection button shows loading state
- [ ] Success/Error messages appear correctly

## Future Enhancements

1. **Support Multiple Providers**: Extend switch-case in service for OpenAI, Azure, etc.
2. **Advanced Testing**: Full response validation, JSON parsing
3. **Audit Logging**: Track who changes configs and when
4. **Environment Variables**: Allow config per environment (dev/staging/prod)
5. **Rate Limiting**: Prevent abuse of test-connection endpoint

## Dependencies Added

No external dependencies required. Uses:
- Existing Spring WebFlux WebClient (for HTTP testing)
- Existing Encryption utilities
- Existing Admin authentication middleware

## File Locations Summary

### Backend
- Entity: `AppBackend/src/main/java/com/example/appbackend/entity/AiModelConfig.java`
- Repository: `AppBackend/src/main/java/com/example/appbackend/repository/AiModelConfigRepository.java`
- DTO: `AppBackend/src/main/java/com/example/appbackend/dto/AiModelConfigDTO.java`
- Service: `AppBackend/src/main/java/com/example/appbackend/service/AiModelConfigService.java`
- Controller: `AppBackend/src/main/java/com/example/appbackend/controller/AiModelConfigController.java`
- SQL Schema: `AppBackend/src/main/resources/ai-model-config.sql`
- Init Data: `AppBackend/src/main/resources/init-ai-model-config.sql`

### Frontend (AppWeb)
- Page: `AppWeb/src/pages/ai/AiModelConfig/AiModelConfig.jsx`
- CSS: `AppWeb/src/pages/ai/AiModelConfig/AiModelConfig.css`
- API: `AppWeb/src/api/aiModelConfig.js`

---

**Last Updated**: 2026-08-21  
**Version**: 1.0  
**Author**: Qoder AI Assistant

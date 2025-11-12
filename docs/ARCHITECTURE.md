# Arquitetura Protheo MVP

## Visão Geral

Protheo é uma plataforma serverless construída 100% em serviços gerenciados AWS, seguindo princípios de:

- **Serverless-first**: Sem servidores para gerenciar
- **Pay-per-use**: Custo proporcional ao uso real
- **Auto-scaling**: Escala automaticamente sob demanda
- **High availability**: Multi-AZ por padrão
- **Security by design**: IAM, encryption, HTTPS

## Diagrama de Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                         FRONTEND (Vue 3)                         │
│                    S3 + CloudFront (CDN)                         │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway HTTP API                          │
│                    (Cognito Authorizer)                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Lambda     │    │   Lambda     │    │   Lambda     │
│   Auth       │    │   Cases      │    │   Payment    │
│  (SnapStart) │    │  (SnapStart) │    │  (SnapStart) │
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                    │
       ▼                   ▼                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                         DynamoDB                                 │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌──────────┐            │
│  │  Users  │ │  Cases  │ │ Payments │ │ Reviews  │  ...       │
│  └─────────┘ └─────────┘ └──────────┘ └──────────┘            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     S3 (Files Bucket)                            │
│              STL files, photos (presigned URLs)                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         Stripe API                               │
│         Payment Intents + Transfers (Escrow)                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    EventBridge (Scheduler)                       │
│           Payment Release (hourly check)                         │
└─────────────────────────────────────────────────────────────────┘
```

## Componentes Principais

### 1. Frontend (Vue 3 SPA)

**Tecnologias**:
- Vue 3 (Composition API)
- Vite (build tool)
- Tailwind CSS (styling)
- Pinia (state management)
- Vue Router (routing)
- Three.js (visualizador 3D STL)

**Hospedagem**:
- S3 (static website hosting)
- CloudFront (CDN global)
- Cache agressivo para assets (31536000s = 1 ano)
- index.html sem cache (sempre fresh)

**Autenticação**:
- Amazon Cognito SDK
- JWT tokens (ID token + refresh token)
- Auto-refresh quando token expira

### 2. Backend (Java + Quarkus)

**Tecnologias**:
- Java 17
- Quarkus (JVM mode)
- AWS SDK v2
- Stripe Java SDK
- Jackson (JSON)

**Handlers Lambda** (10 total):

| Handler | Responsabilidade | Trigger |
|---------|------------------|---------|
| `AuthHandler` | Login/registro Cognito | HTTP API |
| `LabsHandler` | CRUD laboratórios | HTTP API |
| `CasesHandler` | CRUD casos clínicos | HTTP API |
| `PresignHandler` | Gerar presigned URLs | HTTP API |
| `LabActionsHandler` | Aceitar/recusar casos | HTTP API |
| `BudgetHandler` | Criar/aprovar orçamentos | HTTP API |
| `ProjectSubmissionHandler` | Lab envia STL final | HTTP API |
| `ProjectApprovalHandler` | Dentista aprova/revisa | HTTP API |
| `PaymentHandler` | Processar pagamentos Stripe | HTTP API + Webhook |
| `PaymentReleaseHandler` | Liberar pagamentos (scheduled) | EventBridge |
| `ShippingHandler` | Confirmar envio | HTTP API |
| `DisputeHandler` | Gerenciar contestações | HTTP API |
| `ReviewHandler` | Avaliações | HTTP API |
| `GdprHandler` | Exportar/deletar dados | HTTP API |

**Otimizações**:
- ✅ **SnapStart** habilitado (reduz cold start ~90%)
- ✅ Priming hooks para carregar conexões
- ✅ Funções pequenas e específicas
- ✅ AWS SDK v2 (assíncrono)
- ✅ Sem estado mutável global

### 3. Banco de Dados (DynamoDB)

**Tabelas** (7 total):

#### `users`
```
PK: userId
GSI: email-index (email)
GSI: cognito-index (cognitoSub)

Atributos:
- userId, email, name, role (dentist/lab)
- cognitoSub, createdAt
```

#### `labs`
```
PK: labId
GSI: uf-index (uf)

Atributos:
- labId, name, uf, city, materials[]
- sla, averageRating, totalReviews
- stripeAccountId, active
```

#### `cases`
```
PK: caseId
GSI: dentist-index (dentistId, createdAt)
GSI: lab-index (labId, createdAt)
GSI: status-index (status, createdAt)

Atributos:
- caseId, dentistId, labId, status
- title, description, filesKeys[]
- projectFileKey, revisionNotes, revisionCount
- createdAt, updatedAt
```

#### `budgets`
```
PK: budgetId
GSI: case-index (caseId)

Atributos:
- budgetId, caseId, labId
- amount, currency, breakdown[]
- validUntil, status
- createdAt, approvedAt
```

#### `payments`
```
PK: paymentId
GSI: case-index (caseId)
GSI: status-deadline-index (status, disputeDeadline)

Atributos:
- paymentId, caseId, stripePaymentIntentId
- totalAmount, commission, labAmount
- status, transfers[], holdAmount
- disputeDeadline, shippedAt, trackingCode
```

#### `reviews`
```
PK: reviewId
GSI: lab-index (labId, createdAt)
GSI: case-index (caseId)

Atributos:
- reviewId, caseId, dentistId, labId
- rating, comment, createdAt
```

#### `consents`
```
PK: consentId
GSI: user-index (userId)

Atributos:
- consentId, userId, acceptedAt, ipAddress
```

**Estratégia de acesso**:
- Pay-per-request (sem provisionamento)
- GSIs para queries eficientes
- Streams habilitados (auditoria futura)

### 4. Armazenamento de Arquivos (S3)

**Files Bucket**:
- Privado (presigned URLs)
- Lifecycle: 180 dias (configurável)
- Versionamento habilitado
- CORS configurado para upload direto

**Estrutura**:
```
s3://protheo-files-{env}-{account}/
├── cases/
│   ├── {caseId}/
│   │   ├── stl_1.stl
│   │   ├── photo_1.jpg
│   │   └── final_project.stl
```

**Presigned URLs**:
- Upload: 15 minutos de validade
- Download: 15 minutos de validade
- Gerados via Lambda (PresignHandler)

### 5. Autenticação (Cognito)

**User Pool**:
- Email como username
- Password policy: 8+ chars, upper+lower+number
- Auto-verified email
- Custom attribute: `role` (dentist/lab)

**Tokens**:
- Access token: 1 hora
- ID token: 1 hora
- Refresh token: 30 dias

**Integração**:
- API Gateway Authorizer (JWT validation)
- Frontend: amazon-cognito-identity-js

### 6. Pagamentos (Stripe)

**Modelo de Escrow**:

```
1. Dentista paga → Captura imediata para Protheo
   💰 R$ 500 vai para conta Protheo
   
2. Dentista aprova STL → Transfer 70% para lab
   💰 Protheo transfere R$ 350 (70%) para lab
   
3. Lab despacha → Aguarda 14 dias
   
4. Sem contestação → Transfer 30% restante
   💰 Protheo transfere R$ 135 (30% - comissão)
   💰 Protheo fica com R$ 50 (10% comissão)
```

**Implementação**:
- Payment Intent (captura imediata)
- Transfers para contas Connect
- Webhooks para eventos
- Metadata para rastreamento

### 7. Filas e Eventos

**SQS**:
- `cases-queue`: Processamento assíncrono de casos
- Visibility timeout: 60s
- Retention: 14 dias

**EventBridge**:
- Scheduled rule: `rate(1 hour)`
- Target: PaymentReleaseHandler
- Verifica pagamentos com prazo expirado

## Fluxo de Dados

### 1. Criação de Caso

```
Dentista → Frontend → API Gateway → CasesHandler → DynamoDB
                                  ↓
                              SQS Queue
```

### 2. Upload de Arquivo

```
Dentista → Frontend → PresignHandler → Presigned URL
                                     ↓
Frontend → S3 (upload direto)
```

### 3. Pagamento com Escrow

```
Dentista → Frontend → PaymentHandler → Stripe (Payment Intent)
                                     ↓
                                 DynamoDB (payment: PENDING)
                                     ↓
Stripe Webhook → PaymentHandler → DynamoDB (payment: HELD)
                                     ↓
Dentista aprova STL → ProjectApprovalHandler → PaymentService
                                              ↓
                                    Stripe Transfer (70%)
                                              ↓
                                    DynamoDB (payment: PARTIAL_RELEASED)
                                              ↓
Lab despacha → ShippingHandler → DynamoDB (disputeDeadline)
                                              ↓
EventBridge (hourly) → PaymentReleaseHandler → Check deadline
                                              ↓
                                    Stripe Transfer (30%)
                                              ↓
                                    DynamoDB (payment: COMPLETED)
```

## Segurança

### Camadas de Proteção

1. **Network**:
   - CloudFront (DDoS protection)
   - API Gateway (rate limiting)
   - VPC endpoints (opcional)

2. **Authentication**:
   - Cognito (MFA opcional)
   - JWT tokens (short-lived)
   - Refresh token rotation

3. **Authorization**:
   - IAM roles (least privilege)
   - API Gateway authorizer
   - Resource-based policies

4. **Data**:
   - Encryption at rest (DynamoDB, S3)
   - Encryption in transit (HTTPS)
   - Presigned URLs (time-limited)

5. **Secrets**:
   - Secrets Manager (Stripe keys)
   - Environment variables (config)

### LGPD Compliance

- ✅ Consentimento explícito
- ✅ Endpoint de exportação de dados
- ✅ Endpoint de deleção de dados
- ✅ TTL automático em S3
- ✅ Logs de auditoria (CloudWatch)

## Escalabilidade

### Auto-scaling

Todos os serviços escalam automaticamente:

- **Lambda**: 1000 concurrent executions (padrão)
- **DynamoDB**: Pay-per-request (ilimitado)
- **S3**: Ilimitado
- **CloudFront**: Global edge locations
- **API Gateway**: 10k requests/second (padrão)

### Limites e Quotas

| Serviço | Limite Padrão | Como Aumentar |
|---------|---------------|---------------|
| Lambda concurrent executions | 1000 | Service Quotas |
| API Gateway requests/second | 10,000 | Service Quotas |
| DynamoDB throughput | Ilimitado (PAR) | N/A |
| S3 requests/second | 5,500 GET, 3,500 PUT | Automático |

## Monitoramento

### CloudWatch Metrics

**Lambda**:
- Invocations
- Duration (p50, p90, p99)
- Errors
- Throttles
- ConcurrentExecutions
- SnapStart (CacheHitCount)

**API Gateway**:
- Count
- Latency
- 4XXError
- 5XXError

**DynamoDB**:
- ConsumedReadCapacityUnits
- ConsumedWriteCapacityUnits
- UserErrors
- SystemErrors

**S3**:
- BucketSizeBytes
- NumberOfObjects
- AllRequests
- 4xxErrors

### Logs

- CloudWatch Logs (retention: 7 dias)
- Structured logging (JSON)
- Correlation IDs (rastreamento)

### Alertas

- Billing > $5
- Lambda errors > 10/min
- API Gateway 5xx > 5%
- DynamoDB throttles > 0

## Custos

### Breakdown Mensal (após free tier)

| Serviço | Uso Estimado | Custo |
|---------|--------------|-------|
| Lambda | 100k invocations | $0.20 |
| API Gateway | 100k requests | $0.10 |
| DynamoDB | 1GB + 10M reads | $0.25 |
| S3 | 10GB + 100k requests | $0.50 |
| CloudFront | 10GB egress | $0.85 |
| Secrets Manager | 1 secret | $0.40 |
| **Total** | | **~$2.30** |

### Otimizações de Custo

- ✅ HTTP API (não REST API) → 70% mais barato
- ✅ CloudFront cache → Reduz egress S3
- ✅ DynamoDB PAR → Sem over-provisioning
- ✅ Lambda SnapStart → Sem Provisioned Concurrency
- ✅ S3 Lifecycle → Deleta arquivos antigos

## Disaster Recovery

### Backup

- **DynamoDB**: Point-in-time recovery (PITR)
- **S3**: Versionamento habilitado
- **Código**: GitHub (source of truth)

### Rollback

```bash
# Rollback via CloudFormation
aws cloudformation update-stack \
  --stack-name protheo-prod \
  --use-previous-template

# Rollback via SAM
sam deploy --no-confirm-changeset --rollback
```

### RTO/RPO

- **RTO**: < 1 hora (redeploy stack)
- **RPO**: < 5 minutos (DynamoDB PITR)

## Evolução Futura

### Fase 2

- [ ] WebSocket (notificações realtime)
- [ ] ElastiCache (cache de queries)
- [ ] Step Functions (workflows complexos)
- [ ] SES (emails transacionais)

### Fase 3

- [ ] Multi-region (disaster recovery)
- [ ] Aurora Serverless (analytics)
- [ ] Kinesis (event streaming)
- [ ] SageMaker (IA para análise STL)

---

**Última atualização**: 2025-01-12

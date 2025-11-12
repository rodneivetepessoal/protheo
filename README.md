# Protheo MVP - Marketplace Odontológico Serverless

![Architecture](docs/architecture-diagram.png)

**Protheo** é uma plataforma marketplace serverless que conecta dentistas a laboratórios de próteses dentárias, seguindo o modelo de negócio UBER com sistema de escrow (retenção de pagamento) para proteção de ambas as partes.

## 🎯 Visão Geral

### Modelo de Negócio

- **Marketplace bidirecional**: Dentistas encontram laboratórios qualificados
- **Comissionamento**: Protheo recebe % de cada transação (configurável)
- **Pagamento seguro**: Sistema de escrow com liberação fracionada
- **Qualidade garantida**: Sistema de avaliações e revisões gratuitas
- **Transparência**: Orçamento antes de iniciar trabalho

### Fluxo Completo

```
1. Dentista cria caso + upload STL/fotos
   ↓
2. Dentista seleciona laboratório e envia caso
   ↓
3. Laboratório aceita e envia ORÇAMENTO
   ↓
4. Dentista aprova orçamento e PAGA via Stripe
   💰 Valor fica RETIDO na Protheo
   ↓
5. Laboratório trabalha no projeto 3D
   ↓
6. Laboratório envia STL FINAL para aprovação
   ↓
7. Dentista APROVA projeto digital
   💰 Lab recebe 70% do valor (primeira liberação)
   ↓
8. Laboratório DESPACHA produto físico
   ↓
9. Dentista tem 14 DIAS para contestar
   ↓
10. Sem contestação: Lab recebe 30% restante
    💰 Pagamento completo ao laboratório
   ↓
11. Dentista AVALIA laboratório (1-5 estrelas)
   ↓
12. Caso finalizado ✅
```

## 🏗️ Arquitetura

### Stack Tecnológica

- **Backend**: Java 17 + Quarkus (Lambda SnapStart)
- **Frontend**: Vue 3 + Vite + Tailwind CSS + Pinia
- **Infraestrutura**: AWS SAM (Serverless Application Model)
- **Pagamentos**: Stripe (Payment Intents + Transfers)
- **CI/CD**: GitHub Actions

### Serviços AWS

| Serviço | Uso | Custo (Free Tier) |
|---------|-----|-------------------|
| **Lambda** | Handlers Java (SnapStart) | 1M requests/mês grátis |
| **API Gateway HTTP API** | REST API | 1M requests/mês (12 meses) |
| **DynamoDB** | Banco NoSQL | 25 GB + 25 RCU/WCU (always free) |
| **S3** | Arquivos STL/fotos + Frontend | 5 GB + 20k GET (12 meses) |
| **CloudFront** | CDN global | 1 TB egress (12 meses) |
| **Cognito** | Autenticação | 50k MAU (always free) |
| **SQS** | Filas de processamento | 1M requests/mês (always free) |
| **Secrets Manager** | Chaves Stripe | $0.40/secret/mês |

**Estimativa mensal (após free tier)**: < $5/mês para uso baixo/moderado

## ⚙️ Configurações Customizáveis

Todas as porcentagens e prazos são **configuráveis via parâmetros SAM**, permitindo ajustes sem redeployar código:

| Parâmetro | Padrão | Descrição |
|-----------|--------|-----------|
| `CommissionPercentage` | 10% | Comissão da Protheo |
| `FirstReleasePercentage` | 70% | Liberação após aprovação digital |
| `DisputePeriodDays` | 14 dias | Prazo para contestação |
| `MaxFreeRevisions` | 2 | Revisões gratuitas |
| `BudgetValidityDays` | 7 dias | Validade do orçamento |

### Como Alterar Configurações

1. **Via Parâmetros SAM** (recomendado):
   ```bash
   # Editar arquivo de parâmetros
   vim infra/parameters/prod.json
   
   # Exemplo:
   {
     "Parameters": {
       "CommissionPercentage": "15",
       "FirstReleasePercentage": "60",
       "DisputePeriodDays": "21"
     }
   }
   
   # Redeploy
   sam deploy --parameter-overrides $(cat infra/parameters/prod.json | jq -r '.Parameters | to_entries | map("\(.key)=\(.value)") | join(" ")')
   ```

2. **Via Console AWS CloudFormation**:
   - Acesse stack `protheo-{environment}`
   - Update Stack → Use current template
   - Altere parâmetros desejados
   - Confirme update

## 🚀 Deploy

### Pré-requisitos

- Conta AWS (free tier recomendado)
- AWS CLI configurado
- SAM CLI instalado
- Java 17 + Maven
- Node.js 20+
- Conta Stripe (test mode)

### 1. Configurar Secrets

```bash
# Criar secret do Stripe no Secrets Manager
aws secretsmanager create-secret \
  --name protheo/stripe/dev \
  --secret-string '{
    "secret_key": "sk_test_YOUR_KEY",
    "webhook_secret": "whsec_YOUR_SECRET",
    "publishable_key": "pk_test_YOUR_KEY"
  }'
```

### 2. Deploy Backend (SAM)

```bash
cd infra

# Build
sam build --use-container

# Deploy
sam deploy \
  --guided \
  --stack-name protheo-dev \
  --parameter-overrides file://parameters/dev.json \
  --capabilities CAPABILITY_IAM
```

### 3. Deploy Frontend

```bash
cd frontend

# Instalar dependências
npm install

# Criar .env
cp .env.example .env
# Editar .env com valores do stack SAM

# Build
npm run build

# Deploy para S3
BUCKET=$(aws cloudformation describe-stacks \
  --stack-name protheo-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`FrontendBucketName`].OutputValue' \
  --output text)

aws s3 sync dist/ s3://$BUCKET/ --delete
```

### 4. Seed de Dados

```bash
cd scripts

# Popular DynamoDB com dados de exemplo
python3 seed-dynamodb.py --environment dev --labs 10 --cases 100
```

## 🔧 Desenvolvimento Local

### Backend (SAM Local)

```bash
cd infra

# Iniciar API local
sam local start-api --parameter-overrides file://parameters/dev.json

# Testar função específica
sam local invoke AuthFunction --event events/login.json
```

### Frontend

```bash
cd frontend

# Dev server
npm run dev

# Acesse http://localhost:3000
```

## 📊 Monitoramento

### CloudWatch Dashboard

Acesse o dashboard criado automaticamente:
- Invocações Lambda
- Erros e throttles
- Latência (cold starts vs warm)
- Custos S3/CloudFront

### Billing Alerts

Configurar alerta de custo:

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name protheo-billing-alert \
  --alarm-description "Alert when estimated charges exceed $5" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 21600 \
  --evaluation-periods 1 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold
```

## 🧪 Testes

### Unit Tests (Backend)

```bash
cd backend
mvn test
```

### E2E Tests (Frontend)

```bash
cd frontend
npm run test:e2e
```

## 📁 Estrutura do Projeto

```
protheo-mvp/
├── infra/                      # AWS SAM templates
│   ├── template.yaml           # Template principal
│   ├── parameters/             # Parâmetros por ambiente
│   │   ├── dev.json
│   │   ├── staging.json
│   │   └── prod.json
│   └── scripts/
│       ├── deploy.sh
│       └── rollback.sh
├── backend/                    # Backend Java + Quarkus
│   ├── pom.xml
│   ├── src/main/java/com/protheo/
│   │   ├── auth/               # Autenticação
│   │   ├── labs/               # Laboratórios
│   │   ├── cases/              # Casos clínicos
│   │   ├── budget/             # Orçamentos
│   │   ├── payment/            # Pagamentos (Stripe)
│   │   ├── project/            # Projetos 3D
│   │   ├── shipping/           # Envio
│   │   ├── dispute/            # Contestações
│   │   ├── review/             # Avaliações
│   │   ├── files/              # Presigned URLs
│   │   ├── gdpr/               # LGPD compliance
│   │   ├── config/             # Configurações
│   │   ├── model/              # Modelos de domínio
│   │   └── service/            # Serviços
│   └── src/test/               # Testes
├── frontend/                   # Frontend Vue 3
│   ├── package.json
│   ├── vite.config.js
│   ├── tailwind.config.js
│   ├── src/
│   │   ├── views/              # Páginas
│   │   ├── components/         # Componentes
│   │   ├── stores/             # Pinia stores
│   │   ├── services/           # API clients
│   │   └── router/             # Vue Router
│   └── dist/                   # Build output
├── scripts/                    # Scripts utilitários
│   └── seed-dynamodb.py        # Seed de dados
├── docs/                       # Documentação
│   ├── architecture.md
│   ├── data-model.md
│   └── api-spec.md
├── .github/workflows/          # CI/CD
│   └── deploy.yml
└── README.md
```

## 🔐 Segurança e LGPD

### Implementações

- ✅ Presigned URLs com expiração curta (15 min)
- ✅ Tokens JWT via Cognito
- ✅ Consentimento explícito armazenado
- ✅ Endpoints de exportação/deleção de dados
- ✅ TTL em S3 (180 dias por padrão)
- ✅ Todas as comunicações via HTTPS/CloudFront

### Endpoints LGPD

```bash
# Exportar dados do usuário
POST /gdpr/export

# Deletar dados do usuário
POST /gdpr/delete
```

## 💳 Integração Stripe

### Configuração

1. Criar conta Stripe (test mode)
2. Habilitar Stripe Connect
3. Configurar webhook endpoint: `{API_URL}/webhooks/stripe`
4. Adicionar secret keys no Secrets Manager

### Eventos Stripe

- `payment_intent.succeeded` → Confirma pagamento
- `transfer.created` → Registra transferência ao lab

## 🎨 Customização de UI

### Cores (Tailwind)

Editar `frontend/tailwind.config.js`:

```js
colors: {
  primary: { ... },   // Cor principal
  secondary: { ... }  // Cor secundária
}
```

### Logo

Substituir `frontend/src/assets/logo.png`

## 🐛 Troubleshooting

### Lambda Cold Starts

- ✅ SnapStart habilitado (reduz cold start Java em ~90%)
- ✅ Funções pequenas e específicas
- ✅ Priming hooks implementados

### Erros Comuns

**"Access Denied" no S3**:
```bash
# Verificar bucket policy
aws s3api get-bucket-policy --bucket protheo-frontend-dev-{account-id}
```

**"Invalid token" no API Gateway**:
```bash
# Verificar configuração Cognito
aws cognito-idp describe-user-pool --user-pool-id {pool-id}
```

## 📈 Roadmap

- [ ] Notificações realtime (WebSocket)
- [ ] Chat entre dentista e laboratório
- [ ] Integração com correios (rastreamento)
- [ ] App mobile (React Native)
- [ ] IA para análise de STL
- [ ] Multi-idioma (i18n)

## 📝 Licença

MIT License - veja [LICENSE](LICENSE)

## 🤝 Contribuindo

1. Fork o projeto
2. Crie uma branch (`git checkout -b feature/nova-funcionalidade`)
3. Commit suas mudanças (`git commit -m 'Adiciona nova funcionalidade'`)
4. Push para a branch (`git push origin feature/nova-funcionalidade`)
5. Abra um Pull Request

## 📧 Suporte

- **Documentação**: [docs/](docs/)
- **Issues**: [GitHub Issues](https://github.com/seu-usuario/protheo-mvp/issues)
- **Email**: suporte@protheo.com.br

---

**Desenvolvido com ❤️ para revolucionar o mercado odontológico**

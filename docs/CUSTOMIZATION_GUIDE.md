# Guia de Customização - Protheo MVP

Este guia explica como customizar as porcentagens de pagamento e outras configurações do sistema **sem precisar alterar código**.

## 📊 Configurações Disponíveis

Todas as configurações abaixo podem ser alteradas via **parâmetros SAM** ou **variáveis de ambiente**:

| Configuração | Padrão | Mín | Máx | Descrição |
|--------------|--------|-----|-----|-----------|
| **CommissionPercentage** | 10% | 0% | 30% | Comissão da Protheo sobre cada transação |
| **FirstReleasePercentage** | 70% | 0% | 100% | Porcentagem liberada após aprovação do projeto digital |
| **DisputePeriodDays** | 14 dias | 1 | 30 | Dias para contestação após recebimento do produto |
| **MaxFreeRevisions** | 2 | 0 | 10 | Número de revisões gratuitas permitidas |
| **BudgetValidityDays** | 7 dias | 1 | 30 | Dias de validade do orçamento |

## 🎯 Cenários de Customização

### Cenário 1: Aumentar Comissão para 15%

**Quando usar**: Aumentar receita da plataforma

**Impacto**:
- Protheo recebe 15% em vez de 10%
- Laboratório recebe 85% em vez de 90%
- Dentista paga o mesmo valor

**Como fazer**:

```bash
# Editar arquivo de parâmetros
vim infra/parameters/prod.json

# Alterar:
{
  "Parameters": {
    "CommissionPercentage": "15"
  }
}

# Redeploy
cd infra
sam deploy --parameter-overrides CommissionPercentage=15
```

**Exemplo numérico**:
- Orçamento: R$ 1.000,00
- Comissão Protheo: R$ 150,00 (15%)
- Lab recebe: R$ 850,00
  - 1ª liberação (70%): R$ 595,00
  - 2ª liberação (30%): R$ 255,00

---

### Cenário 2: Liberar 80% na Aprovação Digital

**Quando usar**: Dar mais segurança ao laboratório

**Impacto**:
- Lab recebe 80% após aprovação digital
- Lab recebe 20% após período de contestação
- Risco maior para dentista (menos retenção)

**Como fazer**:

```bash
# Via parâmetros SAM
sam deploy --parameter-overrides FirstReleasePercentage=80

# Ou editar infra/parameters/prod.json:
{
  "Parameters": {
    "FirstReleasePercentage": "80"
  }
}
```

**Exemplo numérico**:
- Orçamento: R$ 1.000,00
- Comissão Protheo: R$ 100,00 (10%)
- Lab recebe: R$ 900,00
  - 1ª liberação (80%): R$ 720,00
  - 2ª liberação (20%): R$ 180,00

---

### Cenário 3: Reduzir Prazo de Contestação para 7 Dias

**Quando usar**: Acelerar liberação de pagamento para labs

**Impacto**:
- Lab recebe saldo restante mais rápido
- Dentista tem menos tempo para contestar

**Como fazer**:

```bash
sam deploy --parameter-overrides DisputePeriodDays=7
```

---

### Cenário 4: Permitir 5 Revisões Gratuitas

**Quando usar**: Garantir maior qualidade do produto final

**Impacto**:
- Dentista pode solicitar até 5 revisões sem custo
- Lab precisa refazer mais vezes sem cobrar extra

**Como fazer**:

```bash
sam deploy --parameter-overrides MaxFreeRevisions=5
```

---

## 🔧 Métodos de Customização

### Método 1: Via Arquivo de Parâmetros (Recomendado)

**Vantagens**:
- ✅ Versionado no Git
- ✅ Fácil auditoria
- ✅ Diferentes configs por ambiente

**Passo a passo**:

```bash
# 1. Editar arquivo de parâmetros
vim infra/parameters/prod.json

# 2. Alterar valores desejados
{
  "Parameters": {
    "Environment": "prod",
    "CommissionPercentage": "12",
    "FirstReleasePercentage": "75",
    "DisputePeriodDays": "10",
    "MaxFreeRevisions": "3",
    "BudgetValidityDays": "5"
  }
}

# 3. Redeploy
cd infra
sam deploy --parameter-overrides $(cat parameters/prod.json | jq -r '.Parameters | to_entries | map("\(.key)=\(.value)") | join(" ")')
```

---

### Método 2: Via Console AWS CloudFormation

**Vantagens**:
- ✅ Interface visual
- ✅ Não precisa de CLI

**Passo a passo**:

1. Acesse AWS Console → CloudFormation
2. Selecione stack `protheo-prod`
3. Clique em **Update**
4. Selecione **Use current template**
5. Altere parâmetros desejados
6. Clique em **Next** → **Next** → **Update stack**

---

### Método 3: Via CLI (Direto)

**Vantagens**:
- ✅ Rápido para testes
- ✅ Não precisa editar arquivo

**Passo a passo**:

```bash
sam deploy \
  --stack-name protheo-prod \
  --parameter-overrides \
    CommissionPercentage=12 \
    FirstReleasePercentage=75 \
    DisputePeriodDays=10
```

---

## 📈 Impacto das Mudanças

### Tabela de Simulação

Orçamento base: **R$ 1.000,00**

| Comissão | 1ª Liberação | Lab (1ª) | Lab (2ª) | Lab (Total) | Protheo |
|----------|--------------|----------|----------|-------------|---------|
| 10% | 70% | R$ 630 | R$ 270 | R$ 900 | R$ 100 |
| 10% | 80% | R$ 720 | R$ 180 | R$ 900 | R$ 100 |
| 15% | 70% | R$ 595 | R$ 255 | R$ 850 | R$ 150 |
| 15% | 80% | R$ 680 | R$ 170 | R$ 850 | R$ 150 |
| 20% | 70% | R$ 560 | R$ 240 | R$ 800 | R$ 200 |
| 5% | 70% | R$ 665 | R$ 285 | R$ 950 | R$ 50 |

### Calculadora Online

Use a calculadora no frontend:

```
https://seu-dominio.com/admin/calculator
```

---

## 🔄 Sincronização Backend ↔ Frontend

### Backend (SAM Parameters)

Configurações são injetadas como **variáveis de ambiente** nas Lambdas:

```yaml
# template.yaml
Environment:
  Variables:
    COMMISSION_PERCENTAGE: !Ref CommissionPercentage
    FIRST_RELEASE_PERCENTAGE: !Ref FirstReleasePercentage
```

### Frontend (.env)

Valores devem ser **sincronizados manualmente** no `.env`:

```bash
# frontend/.env
VITE_COMMISSION_PERCENTAGE=10
VITE_FIRST_RELEASE_PERCENTAGE=70
VITE_DISPUTE_PERIOD_DAYS=14
```

**⚠️ Importante**: Após alterar parâmetros SAM, **rebuilde o frontend** com os novos valores!

```bash
# 1. Atualizar .env
vim frontend/.env

# 2. Rebuild
cd frontend
npm run build

# 3. Deploy
aws s3 sync dist/ s3://protheo-frontend-prod-{account}/ --delete
```

---

## 🧪 Testando Mudanças

### 1. Ambiente de Staging

Sempre teste em **staging** antes de produção:

```bash
# Deploy em staging
sam deploy \
  --stack-name protheo-staging \
  --parameter-overrides CommissionPercentage=15

# Testar manualmente
# ...

# Se OK, deploy em prod
sam deploy \
  --stack-name protheo-prod \
  --parameter-overrides CommissionPercentage=15
```

### 2. Validação

Após deploy, valide:

```bash
# 1. Verificar parâmetros aplicados
aws cloudformation describe-stacks \
  --stack-name protheo-prod \
  --query 'Stacks[0].Parameters'

# 2. Verificar outputs
aws cloudformation describe-stacks \
  --stack-name protheo-prod \
  --query 'Stacks[0].Outputs'

# 3. Testar endpoint
curl https://api.protheo.com/health
```

### 3. Monitoramento

Monitore métricas após mudança:

- CloudWatch Logs (erros)
- CloudWatch Metrics (latência)
- Stripe Dashboard (transações)

---

## 📋 Checklist de Mudança

Antes de alterar configurações em produção:

- [ ] Testar em ambiente de staging
- [ ] Validar cálculos com calculadora
- [ ] Atualizar documentação interna
- [ ] Notificar equipe comercial
- [ ] Atualizar termos de uso (se aplicável)
- [ ] Comunicar laboratórios (se reduzir %)
- [ ] Fazer backup do stack atual
- [ ] Agendar janela de manutenção
- [ ] Preparar rollback plan
- [ ] Monitorar por 24h após mudança

---

## 🔙 Rollback

Se algo der errado, faça rollback:

```bash
# Método 1: Via CloudFormation
aws cloudformation update-stack \
  --stack-name protheo-prod \
  --use-previous-template

# Método 2: Via SAM (requer template anterior)
sam deploy \
  --stack-name protheo-prod \
  --parameter-overrides $(cat parameters/prod.backup.json | jq -r '.Parameters | to_entries | map("\(.key)=\(.value)") | join(" ")')
```

---

## 🎓 Casos de Uso Avançados

### Caso 1: A/B Testing de Comissão

Criar stacks separados para testar diferentes comissões:

```bash
# Stack A: 10% comissão
sam deploy --stack-name protheo-prod-a --parameter-overrides CommissionPercentage=10

# Stack B: 12% comissão
sam deploy --stack-name protheo-prod-b --parameter-overrides CommissionPercentage=12

# Direcionar 50% do tráfego para cada via Route53 weighted routing
```

### Caso 2: Comissão Progressiva por Volume

Implementar lógica no backend:

```java
// PaymentConfig.java
public long calculateCommission(long totalAmount, int labMonthlyVolume) {
    int percentage = commissionPercentage; // Padrão
    
    if (labMonthlyVolume > 100) {
        percentage = 8; // Desconto para alto volume
    } else if (labMonthlyVolume > 50) {
        percentage = 9;
    }
    
    return (totalAmount * percentage) / 100;
}
```

### Caso 3: Comissão por Categoria de Produto

Adicionar parâmetro por material:

```yaml
# template.yaml
Parameters:
  CommissionZirconia:
    Type: Number
    Default: 12
  CommissionPorcelain:
    Type: Number
    Default: 10
```

---

## 📞 Suporte

Dúvidas sobre customização?

- **Documentação**: [docs/](../docs/)
- **Issues**: [GitHub Issues](https://github.com/seu-usuario/protheo-mvp/issues)
- **Email**: tech@protheo.com.br

---

**Última atualização**: 2025-01-12

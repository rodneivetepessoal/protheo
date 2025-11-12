# Painel Administrativo - Protheo MVP

## Visão Geral

O **Painel Administrativo** permite que você (dono do marketplace) gerencie todos os aspectos críticos da plataforma Protheo, incluindo:

- ✅ **Parametrização de comissões** por laboratório
- ✅ **Aprovação/reprovação de contestações**
- ✅ **Gestão de repasses financeiros**
- ✅ **Monitoramento de receita e GMV**
- ✅ **Controle de status de laboratórios**
- ✅ **Logs de auditoria** de todas as ações administrativas

---

## 🔐 Acesso Administrativo

### Configuração de Usuários Admin

Para ter acesso ao painel administrativo, o usuário precisa estar no grupo **"admins"** do Cognito:

```bash
# Adicionar usuário ao grupo admins
aws cognito-idp admin-add-user-to-group \
  --user-pool-id us-east-1_XXXXXXXXX \
  --username admin@protheo.com.br \
  --group-name admins

# Criar grupo admins (se não existir)
aws cognito-idp create-group \
  --user-pool-id us-east-1_XXXXXXXXX \
  --group-name admins \
  --description "Administrators with full access"
```

### Verificação de Acesso

Todos os endpoints administrativos verificam automaticamente se o usuário pertence ao grupo "admins" via JWT token do Cognito. Caso contrário, retorna **403 Forbidden**.

---

## 📊 Dashboard Principal

### Estatísticas Principais

O dashboard exibe métricas em tempo real:

| Métrica | Descrição | Cálculo |
|---------|-----------|---------|
| **Receita Total** | Comissão acumulada da Protheo | Soma de `commission` de todos os pagamentos `COMPLETED` |
| **GMV Total** | Gross Merchandise Value | Soma de `totalAmount` de todos os pagamentos `COMPLETED` |
| **Repasses Pendentes** | Número de pagamentos aguardando liberação | Count de pagamentos com status `PARTIAL_RELEASED` ou `HELD` |
| **Casos Concluídos** | Total de casos finalizados | Count de pagamentos com status `COMPLETED` |

### Gráficos

- **Receita por Mês**: Gráfico de linha mostrando evolução da receita
- **GMV por Mês**: Gráfico de linha mostrando evolução do GMV
- **Receita por Laboratório**: Top 10 laboratórios que mais geraram comissão

---

## 🏥 Gestão de Laboratórios

### Parametrização de Comissões

Você pode definir **comissões customizadas** por laboratório, sobrescrevendo a configuração global:

#### Exemplo: Lab Premium com Comissão Reduzida

```json
{
  "labId": "lab_123",
  "customCommissionPercentage": 5,  // 5% em vez de 10% global
  "customFirstReleasePercentage": 80, // 80% em vez de 70% global
  "customDisputePeriodDays": 7,      // 7 dias em vez de 14 global
  "customMaxFreeRevisions": 3        // 3 revisões em vez de 2 global
}
```

#### Endpoint

```http
PUT /admin/labs/{labId}
Authorization: Bearer {admin_jwt_token}
Content-Type: application/json

{
  "customCommissionPercentage": 5,
  "customFirstReleasePercentage": 80,
  "customDisputePeriodDays": 7,
  "customMaxFreeRevisions": 3
}
```

### Controle de Status

Você pode alterar o status de qualquer laboratório:

| Status | Descrição | Impacto |
|--------|-----------|---------|
| **ACTIVE** | Ativo e operando normalmente | Pode receber casos e repasses |
| **PENDING_REVIEW** | Aguardando revisão do admin | Não pode receber novos casos |
| **SUSPENDED** | Suspenso temporariamente | Não pode receber casos nem repasses |
| **BLOCKED** | Bloqueado permanentemente | Não pode receber casos nem repasses |
| **INACTIVE** | Inativo (desativado pelo próprio lab) | Não pode receber casos |

#### Endpoint

```http
PUT /admin/labs/{labId}/status
Authorization: Bearer {admin_jwt_token}
Content-Type: application/json

{
  "status": "SUSPENDED",
  "reason": "Múltiplas reclamações de qualidade"
}
```

### Configuração de Repasses

Você pode configurar como e quando cada laboratório recebe seus repasses:

#### Tipos de Repasse

| Tipo | Descrição | Quando Usar |
|------|-----------|-------------|
| **IMMEDIATE** | Repasse imediato após cada liberação | Labs confiáveis com histórico |
| **SCHEDULED** | Repasse agendado (semanal/mensal) | Labs novos ou com volume alto |
| **MANUAL** | Repasse manual pelo admin | Labs com problemas ou casos especiais |

#### Frequências de Repasse (para SCHEDULED)

- **DAILY**: Diariamente
- **WEEKLY**: Semanalmente (definir dia da semana)
- **BIWEEKLY**: Quinzenalmente
- **MONTHLY**: Mensalmente (definir dia do mês)

#### Endpoint

```http
PUT /admin/labs/{labId}/transfer-config
Authorization: Bearer {admin_jwt_token}
Content-Type: application/json

{
  "stripeAccountId": "acct_lab123",
  "transferType": "SCHEDULED",
  "transferFrequency": "WEEKLY",
  "transferDayOfWeek": 5,  // Sexta-feira
  "minimumTransferAmount": 10000,  // R$ 100,00 mínimo
  "securityHoldDays": 2  // 2 dias extras de segurança
}
```

---

## ⚖️ Gestão de Disputas

### Listar Disputas

```http
GET /admin/disputes?status=PENDING
Authorization: Bearer {admin_jwt_token}
```

Retorna todas as disputas pendentes de análise.

### Resolver Disputa

Você pode tomar uma decisão sobre a disputa:

| Resolução | Descrição | Ação Financeira |
|-----------|-----------|-----------------|
| **FAVOR_DENTIST** | A favor do dentista | Reembolso total ou parcial |
| **FAVOR_LAB** | A favor do laboratório | Sem reembolso, lab recebe saldo |
| **PARTIAL_REFUND** | Reembolso parcial | Reembolso de X% ao dentista |
| **REMAKE_REQUIRED** | Refazer produto | Lab deve refazer sem custo |
| **MEDIATION_FAILED** | Mediação falhou | Escalar para suporte externo |
| **WITHDRAWN** | Contestação retirada | Dentista desistiu da disputa |

#### Endpoint

```http
POST /admin/disputes/{disputeId}/resolve
Authorization: Bearer {admin_jwt_token}
Content-Type: application/json

{
  "resolution": "PARTIAL_REFUND",
  "notes": "Problema de qualidade parcial, reembolso de 50%"
}
```

### Processar Reembolso

Após resolver a disputa, você pode processar o reembolso:

```http
POST /admin/disputes/{disputeId}/refund
Authorization: Bearer {admin_jwt_token}
Content-Type: application/json

{
  "type": "PARTIAL_REFUND",
  "percentage": 50  // 50% do valor total
}
```

**Tipos de Reembolso**:
- `FULL_REFUND`: Reembolso total ao dentista
- `PARTIAL_REFUND`: Reembolso parcial (especificar %)
- `NO_REFUND`: Sem reembolso
- `CHARGEBACK`: Chargeback (caso extremo)

---

## 💰 Gestão de Repasses

### Listar Repasses Pendentes

```http
GET /admin/transfers/pending
Authorization: Bearer {admin_jwt_token}
```

Retorna todos os pagamentos que ainda têm saldo retido aguardando liberação.

**Resposta**:
```json
{
  "items": [
    {
      "paymentId": "pay_123",
      "caseId": "case_456",
      "labId": "lab_789",
      "holdAmount": 30000,  // R$ 300,00 retido
      "disputeDeadline": "2025-01-26T00:00:00Z",
      "status": "PARTIAL_RELEASED"
    }
  ],
  "count": 5,
  "totalPendingAmount": 150000  // R$ 1.500,00 total retido
}
```

### Processar Repasse Manual

Você pode forçar um repasse manual antes do prazo:

```http
POST /admin/transfers/manual
Authorization: Bearer {admin_jwt_token}
Content-Type: application/json

{
  "paymentId": "pay_123",
  "stripeAccountId": "acct_lab789",
  "amount": 30000,  // R$ 300,00
  "reason": "Liberação antecipada por solicitação do lab"
}
```

**Casos de uso**:
- Lab precisa de liquidez urgente
- Dentista confirmou satisfação antes do prazo
- Erro no sistema que atrasou liberação automática

---

## 📜 Logs de Auditoria

Todas as ações administrativas são registradas automaticamente:

```http
GET /admin/audit-logs?limit=100
Authorization: Bearer {admin_jwt_token}
```

**Campos registrados**:
- `adminId`: ID do admin que fez a ação
- `adminEmail`: Email do admin
- `action`: Tipo de ação (ex: `UPDATE_LAB_COMMISSION`)
- `entityType`: Tipo de entidade afetada (ex: `LAB`, `PAYMENT`)
- `entityId`: ID da entidade
- `oldValues`: Valores antes da mudança
- `newValues`: Valores após a mudança
- `timestamp`: Data/hora da ação
- `ipAddress`: IP do admin (opcional)
- `result`: `SUCCESS` ou `FAILED`

**Ações rastreadas**:
- `UPDATE_LAB_COMMISSION`
- `UPDATE_LAB_STATUS`
- `UPDATE_LAB_TRANSFER_CONFIG`
- `APPROVE_LAB`
- `SUSPEND_LAB`
- `BLOCK_LAB`
- `REVIEW_DISPUTE`
- `RESOLVE_DISPUTE`
- `PROCESS_REFUND`
- `MANUAL_TRANSFER`
- `CANCEL_TRANSFER`
- `ADJUST_PAYMENT`
- `UPDATE_GLOBAL_CONFIG`

---

## 📈 Estatísticas de Receita

### Receita Detalhada por Período

```http
GET /admin/dashboard/revenue?period=30
Authorization: Bearer {admin_jwt_token}
```

**Parâmetros**:
- `period`: Número de dias (ex: 7, 30, 90, 365)

**Resposta**:
```json
{
  "period": "30 days",
  "totalRevenue": 50000,  // R$ 500,00 de comissão
  "totalGMV": 500000,     // R$ 5.000,00 de GMV
  "transactionCount": 25,
  "averageTransactionValue": 20000,  // R$ 200,00 por transação
  "revenueByLab": {
    "lab_123": 15000,
    "lab_456": 12000,
    "lab_789": 10000
  }
}
```

---

## 🔧 Cenários de Uso

### Cenário 1: Lab Premium com Comissão Reduzida

**Situação**: Lab com alto volume e qualidade excepcional merece comissão reduzida.

**Ação**:
1. Acesse **Laboratórios** no painel
2. Clique em **Editar** no lab desejado
3. Defina `customCommissionPercentage: 5` (em vez de 10%)
4. Defina `customFirstReleasePercentage: 85` (mais segurança para o lab)
5. Salvar

**Resultado**: Lab passa a pagar apenas 5% de comissão e recebe 85% após aprovação digital.

---

### Cenário 2: Disputa com Problema de Qualidade

**Situação**: Dentista recebeu prótese com defeito e abriu disputa.

**Ação**:
1. Acesse **Disputas** no painel
2. Analise evidências (fotos, descrição)
3. Entre em contato com lab para ouvir versão
4. Decida: `PARTIAL_REFUND` (50%)
5. Processar reembolso de 50%

**Resultado**: Dentista recebe 50% de volta, lab recebe 50% do valor restante.

---

### Cenário 3: Repasse Manual Antecipado

**Situação**: Lab precisa de liquidez urgente antes do prazo de contestação.

**Ação**:
1. Acesse **Repasses** no painel
2. Localize pagamento pendente
3. Clique em **Repasse Manual**
4. Confirme valor e motivo
5. Processar

**Resultado**: Lab recebe saldo retido imediatamente, mesmo antes do prazo.

---

### Cenário 4: Suspender Lab por Reclamações

**Situação**: Lab acumulou múltiplas reclamações de qualidade.

**Ação**:
1. Acesse **Laboratórios** no painel
2. Clique em **Status** no lab problemático
3. Selecione `SUSPENDED`
4. Informe motivo: "Múltiplas reclamações de qualidade"
5. Salvar

**Resultado**: Lab não pode receber novos casos até revisão.

---

## 🔒 Segurança

### Controle de Acesso

- ✅ Apenas usuários no grupo **"admins"** do Cognito têm acesso
- ✅ JWT token validado em cada request
- ✅ Logs de auditoria registram todas as ações
- ✅ IP address pode ser registrado (opcional)

### Boas Práticas

1. **Nunca compartilhe credenciais admin**
2. **Use MFA (Multi-Factor Authentication)** no Cognito
3. **Revise logs de auditoria regularmente**
4. **Limite número de admins** (princípio do menor privilégio)
5. **Rotacione senhas periodicamente**

---

## 📊 Métricas Recomendadas

### KPIs para Monitorar

| Métrica | Fórmula | Meta |
|---------|---------|------|
| **Taxa de Comissão Efetiva** | (Receita Total / GMV Total) × 100 | 10% |
| **Taxa de Disputa** | (Disputas / Casos Concluídos) × 100 | < 5% |
| **Tempo Médio de Resolução** | Média de (resolvedAt - createdAt) | < 48h |
| **Taxa de Reembolso** | (Reembolsos / Disputas) × 100 | < 30% |
| **Labs Ativos** | Count de labs com status ACTIVE | Crescente |

---

## 🚀 Roadmap Futuro

- [ ] **Dashboard em tempo real** (WebSocket)
- [ ] **Exportação de relatórios** (CSV, PDF)
- [ ] **Alertas automáticos** (email/SMS para disputas críticas)
- [ ] **Análise de fraude** (ML para detectar padrões suspeitos)
- [ ] **Multi-admin com permissões granulares** (RBAC)
- [ ] **Chat integrado** para mediação de disputas
- [ ] **API pública** para integrações externas

---

## 📞 Suporte

Dúvidas sobre o painel administrativo?

- **Documentação**: [docs/](../docs/)
- **Email**: admin@protheo.com.br

---

**Última atualização**: 2025-01-12

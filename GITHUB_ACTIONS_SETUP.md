# GitHub Actions Setup

## ⚠️ Ação Necessária

O arquivo `.github/workflows/deploy.yml` não pôde ser enviado automaticamente devido a restrições de permissão do GitHub.

## Como Adicionar o Workflow Manualmente

### Opção 1: Via Interface do GitHub

1. Acesse: https://github.com/rodneivetepessoal/protheo
2. Clique em **Actions** > **New workflow**
3. Clique em **set up a workflow yourself**
4. Copie o conteúdo do arquivo `.github/workflows/deploy.yml` (disponível localmente)
5. Cole no editor
6. Commit

### Opção 2: Via Git Local (Requer Permissões)

```bash
# Você precisará fazer push diretamente do seu computador
git add .github/workflows/deploy.yml
git commit -m "ci: add GitHub Actions workflow"
git push
```

## Arquivo do Workflow

O arquivo está disponível em: `.github/workflows/deploy.yml`

Ele configura CI/CD automático para:
- Build do backend Java
- Deploy da infraestrutura AWS SAM
- Build e deploy do frontend Vue 3
- Invalidação do CloudFront

## Secrets Necessários no GitHub

Após adicionar o workflow, configure os seguintes secrets:

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION`


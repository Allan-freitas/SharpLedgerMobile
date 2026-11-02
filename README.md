# SharpLedger Mobile

Aplicativo Android para gestão financeira familiar, consumindo a SharpLedger API (`https://agfapp.com`). O app replica as funcionalidades do SharpLedger web com uma interface mobile em Jetpack Compose.

## Funcionalidades

- Login, cadastro, logout e recuperação/redefinição de senha.
- Dashboard mensal com receitas, despesas, saldo inicial, saldo do mês e saldo disponível.
- Gráfico dos últimos 6 meses (receitas x despesas) e rosca com as principais categorias de despesa.
- Exportação do relatório mensal em PDF.
- Navegação entre meses anteriores e futuros.
- Lançamentos de receitas e despesas com descrição, valor, data, categoria e observações.
- Despesas compartilhadas com percentual de divisão.
- Compras parceladas com geração automática das parcelas.
- Anexos em despesas, com upload, download e exclusão.
- Categorias personalizáveis, com ativação/desativação.
- Investimentos: aportes, proventos, preço médio, resultado, rentabilidade e atualização de cotações via brapi.
- Administração da família: código de convite, membros e alteração de papéis.
- Permissões por papel: `Admin`, `Member` e `Viewer`.
- Interface em português (pt-BR) e inglês (en-US), com seletor de idioma.
- Endereço da API configurável no próprio app.

## Stack

- Kotlin + Jetpack Compose + Material 3
- AndroidX Lifecycle / ViewModel / StateFlow
- Retrofit + Gson + OkHttp
- Coroutines
- SharedPreferences para sessão, idioma e endereço da API
- Core library desugaring (`java.time` no Android 24+)
- minSdk 24 / targetSdk 37

## API

O app consome a API REST do SharpLedger com autenticação JWT Bearer.

- URL padrão: `https://agfapp.com/`
- A URL pode ser alterada na tela de login ("Endereço da API") ou em **Mais → Endereço da API**.
- Endpoints utilizados: `/users`, `/categories`, `/budgets`, `/transactions`, `/attachments`, `/investments` e `/reports`.
- JSON em `camelCase`, enums como string e datas em `yyyy-MM-dd`.
- A recuperação de senha gera o deep link `sharpledger://reset-password?email=...&token=...`.

## Estrutura

```text
app/src/main/java/br/com/sharpledger/
  core/            Sessão, preferências, locale, formatação, cliente HTTP e container
  data/            Modelos, interface Retrofit e repositórios
  ui/
    auth/          Login, cadastro, recuperação e redefinição de senha
    dashboard/     Painel, gráficos, saldo e exportação PDF
    transactions/  Lançamentos, parcelas e anexos
    categories/    Categorias de receita e despesa
    investments/   Portfólio, aportes, proventos e cotações
    more/          Família, usuários, senha, idioma, API e sobre
    components/    Componentes Compose reutilizáveis
    theme/         Cores e tipografia
    main/          Navegação inferior e shell principal
```

## Como executar

Pré-requisitos:

- Android Studio
- JDK 25 (toolchain configurado em `gradle/gradle-daemon-jvm.properties`)
- Android SDK 37

Build de debug:

```powershell
.\gradlew.bat assembleDebug
```

APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.

Build de release (sem assinatura):

```powershell
.\gradlew.bat assembleRelease
```

## Testes e lint

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

## Observações

- O app permite tráfego HTTP para facilitar testes locais; em produção utilize HTTPS.
- Anexos e PDFs são salvos em cache e compartilhados via `FileProvider`.
- O ícone do app é adaptativo (API 26+) e também possui versões PNG para API 24–25.

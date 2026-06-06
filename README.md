# Anonimizador PDF

Ferramenta **Android totalmente offline** para anonimizar PDFs clínicos (registros de conversa e
resultados laboratoriais de pacientes) antes de enviá-los a um LLM externo para pesquisa.

O app extrai o texto do PDF **no dispositivo**, usa um **LLM on-device** (MediaPipe) para sugerir
termos sensíveis segundo a **LGPD** — ou permite marcá-los manualmente — substitui cada termo por
`[ANONIMIZADO]` e exporta um `.txt` anonimizado. Nada sai do aparelho.

> ⚠️ Ferramenta pessoal de uso clínico/educacional, **sem fins comerciais**. Licença MIT.

## Privacidade e segurança

- **Sem permissão de INTERNET** — o app não faz nenhuma chamada de rede em tempo de execução.
- `allowBackup=false` (nenhum backup automático de dados clínicos no Google Drive).
- `usesCleartextTraffic=false` + `network_security_config` com tráfego em texto puro desabilitado.
- Sem Firebase / Analytics / Crashlytics / qualquer telemetria. Sem dependências
  `com.google.android.gms`.
- Texto extraído e versões anonimizadas ficam **somente** no Room (armazenamento interno do app).
- Logs nunca incluem o conteúdo do texto (apenas tamanhos, ex.: `"extraction complete, N chars"`).

## Recursos

- Importar PDF via seletor de arquivos (SAF) ou pela folha de compartilhamento do Android.
- Extração de texto com `pdfbox-android` (com indicador de progresso por página).
- Biblioteca: lista, busca por nome, filtros (TODOS / BRUTO / PROCESSADO / ANONIMIZADO),
  arrastar-para-excluir com desfazer.
- Visualizador com seleção de texto, copiar e exportar `.txt`.
- **Modo A (LLM):** sugestões automáticas transmitidas token a token, convertidas em chips
  selecionáveis.
- **Modo B (manual):** seleção de trechos do texto adicionada à lista de termos.
- Pré-visualização com destaque de `[ANONIMIZADO]`, exportação e compartilhamento.
- Layout adaptativo: telefone (painel único + barra de navegação) e tablet (dois painéis
  lista/detalhe + gaveta de navegação).
- Onboarding na primeira execução; tela de configurações (modelo, prompt, pasta de exportação,
  apagar tudo, sobre).

## Stack

Kotlin 2.0 · Jetpack Compose + Material 3 (`adaptive` / `WindowSizeClass`) · Hilt · Room ·
DataStore · Coroutines/Flow · Navigation Compose · MediaPipe `tasks-genai` · `pdfbox-android`.
Arquitetura MVVM + Clean (data / domain / presentation). `minSdk 31`, `targetSdk 35`.

## Pré-requisitos de build

- **JDK 17** (o Android Gradle Plugin 8.7 exige 17+).
- **Android SDK** com a plataforma **API 35** e `build-tools;35.0.0`.
- O wrapper do Gradle (8.10.2) já está incluído — não é necessário instalar o Gradle.

As versões de bibliotecas são fixadas em `gradle/libs.versions.toml` e propositalmente alinhadas ao
Kotlin 2.0.21 (veja as regras de acoplamento em `CLAUDE.md`).

## Compilar e instalar

```bash
# APK de debug -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug

# Instalar em um dispositivo/emulador conectado
./gradlew installDebug

# Testes unitários (lógica pura: redação + parsing do JSON do LLM)
./gradlew testDebugUnitTest
```

### Integração contínua

O build é verificado no **GitHub Actions** (`.github/workflows/android.yml`) em `ubuntu-latest`,
que já possui o Android SDK e acesso de rede. O workflow roda `assembleDebug` + `testDebugUnitTest`
e publica o `app-debug.apk` como artefato do job.

## Carregar um modelo LLM (Gemma 3)

O app **não** embute nenhum modelo. Para usar o Modo A (sugestão automática):

1. Obtenha um modelo MediaPipe LLM no formato **`.task`** (ex.: Gemma 3 1B/4B *instruction-tuned*,
   quantizado). Página oficial do Gemma 3: <https://www.kaggle.com/models/google/gemma-3/>.
   > Caso o modelo seja distribuído via Continuum a partir da página de *releases* do GitHub,
   > nenhuma autenticação/certificação do Google é necessária.
2. Transfira o arquivo `.task` para o armazenamento do dispositivo (ex.: pasta **Downloads**).
3. No app, abra **Configurações → Modelo LLM → Selecionar modelo (.task)** e escolha o arquivo.
   O modelo é copiado para o armazenamento interno do app (o MediaPipe exige um caminho de arquivo,
   não um `content://` Uri).
4. Volte a um documento e toque em **Sugerir Anonimização**.

O **Modo B (manual)** funciona sem nenhum modelo.

## Estrutura

```
app/src/main/java/dev/lorenzods/anonimizadorpdf/
├── data/            # Room (db), DataStore (preferences), repositórios (pdfbox + MediaPipe)
├── domain/          # modelos, interfaces de repositório, use cases (redação + parser puro)
├── presentation/    # navigation (shell adaptativo + NavGraph), theme, ui/{library,viewer,
│                    #   anonymize,settings,onboarding}
└── di/              # módulos Hilt (Database, Repository, UseCase, Dispatcher)
```

## Licença

[MIT](LICENSE). Uso pessoal/educacional, sem fins comerciais.
Repositório: <https://github.com/lonaivdev-cell/anonimizador-pdf>

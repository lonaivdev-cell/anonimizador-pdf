# Anonimizador PDF

Ferramenta **Android totalmente offline** para anonimizar PDFs clínicos (registros de conversa e
resultados laboratoriais de pacientes) antes de enviá-los a um LLM externo para pesquisa.

O app extrai o texto do PDF **no dispositivo** e sugere os termos sensíveis segundo a **LGPD**
instantaneamente, com um **detector offline determinístico** (nomes brasileiros, remetentes de
conversa, CPF, RG, CRM, CNS, telefones, e-mails, endereços, datas, prontuários) — sem precisar de
modelo de IA. Toque nas palavras para marcar/desmarcar, ou refine com um **LLM on-device opcional**.
Cada termo é substituído por `[ANONIMIZADO]` e o resultado é exportado como `.txt` — opcionalmente
**reformatado para leitura por LLM** (alternável). Nada sai do aparelho.

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
- **Sugestões offline instantâneas:** detector determinístico (regex + dicionário de nomes
  brasileiros + padrões de chat) agrupado por categoria, com nível de confiança — alta/média já
  selecionadas, baixa apenas sugerida.
- **Toque-para-marcar:** qualquer palavra do texto vira termo de redação com um toque; campo para
  digitar termos manualmente.
- **IA opcional:** com um modelo carregado, revise a lista de candidatos ou faça uma varredura
  completa do documento (streaming token a token).
- Redação por **palavra inteira** (marcar "Ana" não afeta "Anamnese"), sem diferenciar
  maiúsculas/acentos.
- Pré-visualização com destaque de `[ANONIMIZADO]`, exportação e compartilhamento com nome de
  arquivo neutro (sem vazar o nome original do PDF).
- **Saída organizada para IA (alternável):** títulos, parágrafos reagrupados e artefatos de página
  removidos para facilitar a leitura por um LLM externo; desligue para exportar o texto redigido
  sem alterações.
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

### Publicar uma release (APK)

O artefato do build de CI é temporário e exige login para baixar. Para publicar um APK permanente na
página de **Releases** do GitHub, faça push de uma tag de versão — o workflow
`.github/workflows/release.yml` compila o APK e cria a Release com o APK anexado:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Como alternativa, use **Actions → Release → Run workflow** e informe a versão (ex.: `v1.0.0`).
O APK publicado é assinado com a chave de debug (instalável por sideload).

## Carregar um modelo LLM (Gemma 3)

O app **não** embute nenhum modelo. Para usar o Modo A (sugestão automática):

1. Obtenha um modelo no formato **`.task`** (MediaPipe) **ou `.gguf`** (llama.cpp) — ex.: Gemma 3
   1B/4B *instruction-tuned*, quantizado. O app detecta o motor de inferência pela extensão do
   arquivo, então não é preciso caçar especificamente arquivos `.task`: um `.gguf` quantizado
   (ex.: `Q4_K_M`) funciona diretamente. Página oficial do Gemma 3:
   <https://www.kaggle.com/models/google/gemma-3/>; modelos `.gguf` são facilmente encontrados no
   Hugging Face.
   > Caso o modelo seja distribuído via Continuum a partir da página de *releases* do GitHub,
   > nenhuma autenticação/certificação do Google é necessária.
   > Observação: os binários `.gguf` empacotam apenas as ABIs `arm64-v8a` e `x86_64` (cobrindo
   > celulares reais e emuladores 64 bits).
2. Transfira o arquivo `.task`/`.gguf` para o armazenamento do dispositivo (ex.: pasta **Downloads**).
3. No app, abra **Configurações → Modelo LLM → Selecionar modelo (.task ou .gguf)** e escolha o
   arquivo. O modelo é copiado para o armazenamento interno do app (os motores exigem um caminho de
   arquivo, não um `content://` Uri).
4. Volte a um documento e toque em **Sugerir Anonimização**.

### Baixar modelos com o Termux (mantendo o app offline)

O app **nunca** acessa a rede (não há permissão `INTERNET`), então o download é feito por **outro
app**. O [Termux](https://termux.dev) funciona bem como "baixador": ele tem acesso próprio à
internet e pode gravar na sua área de armazenamento compartilhado, que o seletor de arquivos do app
(SAF) consegue ler.

```bash
# uma única vez:
pkg install python
termux-setup-storage              # cria ~/storage apontando para o armazenamento compartilhado
pip install -U "huggingface_hub[cli]"
# se o modelo exigir login (modelos "gated"), autentique uma vez — o token fica salvo no Termux,
# persistindo entre sessões (~/.cache/huggingface):
huggingface-cli login

# baixar um .gguf para uma pasta compartilhada (NÃO o home privado do Termux):
huggingface-cli download <org>/<modelo-GGUF> <arquivo>.gguf \
  --local-dir ~/storage/downloads/modelos
```

Depois, no app, use **Configurações → Modelo LLM → Selecionar modelo** e navegue até
`Downloads/modelos` para escolher o `.gguf`. O importante é gravar em uma pasta **compartilhada**
(ex.: `~/storage/downloads`), e não no diretório privado do Termux (`/data/data/com.termux/...`),
que o SAF não enxerga.

O **Modo B (manual)** funciona sem nenhum modelo.

## Estrutura

```
app/src/main/java/dev/lorenzods/anonimizadorpdf/
├── data/            # Room (db), DataStore (preferences), repositórios (pdfbox + MediaPipe/llama.cpp)
├── domain/          # modelos, interfaces de repositório, use cases (redação + parser puro)
├── presentation/    # navigation (shell adaptativo + NavGraph), theme, ui/{library,viewer,
│                    #   anonymize,settings,onboarding}
└── di/              # módulos Hilt (Database, Repository, UseCase, Dispatcher)
```

## Licença

[MIT](LICENSE). Uso pessoal/educacional, sem fins comerciais.
Repositório: <https://github.com/lonaivdev-cell/anonimizador-pdf>

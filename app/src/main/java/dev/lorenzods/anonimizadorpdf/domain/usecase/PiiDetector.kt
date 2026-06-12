package dev.lorenzods.anonimizadorpdf.domain.usecase

import dev.lorenzods.anonimizadorpdf.domain.model.Confidence
import dev.lorenzods.anonimizadorpdf.domain.model.RedactionCategory

/**
 * Stage 1 of the anonymization pipeline: a fully offline, deterministic detector that scans clinical
 * text for likely LGPD-sensitive data. It is the **primary** suggestion engine — instant, on-device,
 * no model required. Structured data (CPF, RG, CRM, CRBM, CNS, e-mail, phone, CEP, datas, endereços,
 * prontuário) is matched precisely; person names combine four signals:
 *
 *  1. trigger words ("Paciente:", "Dr.") preceding a capitalised run;
 *  2. chat sender lines ("12/05/2024 14:32 - João: …" and "Maria: …") typical of exported
 *     conversation records;
 *  3. a dictionary of common Brazilian given names/surnames ([BrazilianNames]), which also unlocks
 *     ALL-CAPS names ("PACIENTE: MARIA DA SILVA") that capitalization heuristics alone reject;
 *  4. generic capitalised multi-word runs (low confidence, surfaced unchecked).
 *
 * Confidence drives default selection (HIGH/MEDIUM pre-checked, LOW visible but unchecked), so a
 * false positive never silently corrupts clinical content — the user always confirms. Name runs
 * never cross line breaks or sentence boundaries.
 *
 * Pure logic, no Android/network dependencies — exercised directly by unit tests.
 */
object PiiDetector {

    data class Detection(
        val term: String,
        val category: RedactionCategory,
        val confidence: Confidence,
    )

    // ---- Structured patterns (high precision) ----
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val cpf = Regex("""(?<!\d)\d{3}\.?\d{3}\.?\d{3}-?\d{2}(?!\d)""")
    private val cnpj = Regex("""(?<!\d)\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}(?!\d)""")
    private val cep = Regex("""(?<!\d)\d{5}-\d{3}(?!\d)""")
    private val phone = Regex("""(?<!\d)\(?\d{2}\)?[\s.-]?9?\d{4}[\s.-]?\d{4}(?!\d)""")
    private val numericDate = Regex("""(?<!\d)\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}(?!\d)""")
    private val writtenDate = Regex(
        """(?i)\b\d{1,2}º?\s+de\s+(?:janeiro|fevereiro|mar[çc]o|abril|maio|junho|julho|agosto|""" +
            """setembro|outubro|novembro|dezembro)(?:\s+de\s+\d{2,4})?\b""",
    )

    // Labeled identifiers: only the number (capture group 1) becomes the redaction term, so the
    // label itself stays readable in the output.
    private val rg = Regex("""(?i)\bRG\b[\s:nº°.]*((?:\d{1,2}\.?)?\d{3}\.?\d{3}-?[\dXx]?)""")
    private val crm = Regex("""(?i)\bCRM\b[\s/.:-]*(?:[A-Z]{2}\b)?[\s.:-]*(\d{3,7})""")
    // Conselho Regional de Biomedicina — biomedicine workers sign reports alongside (or instead of)
    // the physician's CRM. The optional region segment may be a UF ("CRBM/SP"), a region number
    // ("CRBM-6"), or a spelled-out region ("CRBM 1ª Região"); a separator is required after it so a
    // bare "CRBM 12345" isn't split into a phantom region + truncated number.
    private val crbm = Regex(
        """(?i)\bCRBM\b[\s/.:-]*(?:(?:[A-Z]{2}|\d{1,2}[ªº]?(?:\s*regi[ãa]o)?)[\s/.:-]+)?(\d{3,7})""",
    )
    private val recordNumber = Regex(
        """(?i)\b(?:prontu[áa]rio|matr[íi]cula|protocolo|registro|atendimento|ficha|amostra|""" +
            """pedido|guia)\b\s*(?:n[ºo°.]*\s*)?[:#]?\s*(\d[\d./-]{2,})""",
    )

    // Cartão Nacional de Saúde: 15 digits, optionally grouped 3-4-4-4.
    private val cns = Regex("""(?<!\d)\d{3}\s?\d{4}\s?\d{4}\s?\d{4}(?!\d)""")

    // Street + name + house number. The number is required: a street mentioned in prose without a
    // number is far more likely to be incidental than an actual patient address.
    private val streetAddress = Regex(
        """\b(?:[Rr]ua|RUA|[Aa]venida|AVENIDA|[Aa][Vv]\.?|[Aa]lameda|ALAMEDA|[Tt]ravessa|""" +
            """TRAVESSA|[Rr]odovia|RODOVIA|[Ee]strada|ESTRADA|[Pp]ra[çc]a|PRA[ÇC]A)\s+""" +
            """(?:d[aoe]s?\s+)?[\p{Lu}][\p{L}\s'’-]{1,40}?,?\s*(?:[Nn][ºo°.]*\s*)?\d{1,5}\b""",
    )

    // Unlabeled long digit runs (sample IDs, badge numbers…). Low confidence: surfaced, unchecked.
    private val longNumber = Regex("""(?<![\d./-])\d{6,}(?![\d.,/-])""")

    private val connectors = setOf("da", "de", "do", "das", "dos", "e", "del", "von", "van", "du")

    // Trigger words that strongly precede a person's name in clinical records.
    private val nameTriggers = setOf(
        "paciente", "nome", "cliente", "sr", "sra", "srta", "dr", "dra", "doutor", "doutora",
        "mãe", "mae", "pai", "responsável", "responsavel", "portador", "portadora",
        "acompanhante", "filho", "filha", "esposo", "esposa", "médico", "medico", "médica",
        "medica", "enfermeiro", "enfermeira", "psicólogo", "psicologo", "psicóloga", "psicologa",
        "nutricionista", "fisioterapeuta", "solicitante", "executante", "convênio", "convenio",
    )

    // Short field labels that must never be swallowed into a name run ("Maria Souza CPF 111…").
    private val labelWords = setOf(
        "cpf", "rg", "cns", "crm", "crbm", "cep", "sus", "cid", "tel", "cel", "fone", "uf", "dn", "rn",
        "id", "os", "nº", "no", "n°", "num",
    )

    // Common capitalised clinical/section words that are NOT names — a candidate made up entirely
    // of these is dropped to keep the offline list usable. Includes frequent lab analytes so report
    // rows ("Ácido Úrico", "Proteínas Totais") don't surface as name suggestions.
    private val clinicalStopwords = setOf(
        "pressão", "pressao", "arterial", "frequência", "frequencia", "cardíaca", "cardiaca",
        "respiratória", "respiratoria", "temperatura", "exame", "exames", "resultado", "resultados",
        "data", "hemograma", "glicose", "colesterol", "observação", "observacao", "conclusão",
        "conclusao", "diagnóstico", "diagnostico", "prescrição", "prescricao", "histórico",
        "historico", "queixa", "conduta", "saturação", "saturacao", "peso", "altura",
        "hemoglobina", "hematócrito", "hematocrito", "leucócitos", "leucocitos", "plaquetas",
        "creatinina", "ureia", "uréia", "glicemia", "ferritina", "vitamina", "sódio", "sodio",
        "potássio", "potassio", "cálcio", "calcio", "magnésio", "magnesio", "fósforo", "fosforo",
        "bilirrubina", "albumina", "hemácias", "hemacias", "eritrócitos", "eritrocitos",
        "neutrófilos", "neutrofilos", "linfócitos", "linfocitos", "monócitos", "monocitos",
        "eosinófilos", "eosinofilos", "basófilos", "basofilos", "triglicerídeos", "triglicerideos",
        "triglicérides", "triglicerides", "urina", "urocultura", "hemocultura", "insulina",
        "método", "metodo", "material", "valor", "valores", "referência", "referencia", "unidade",
        "laudo", "laudos", "avaliação", "avaliacao", "evolução", "evolucao", "anamnese",
        "posologia", "sintomas", "queixas", "hipótese", "hipotese", "alta", "internação",
        "internacao", "retorno", "jejum", "ácido", "acido", "úrico", "urico", "fólico", "folico",
        "gama", "proteínas", "proteinas", "totais", "total", "parcial", "completo", "completa",
        "série", "serie", "vermelha", "branca", "idade", "sexo", "feminino", "masculino",
        // Common form-field labels whose words overlap name dictionaries ("Nascimento" is also a
        // surname) — without these, "Data de Nascimento:" would surface as a person.
        "nascimento", "atendimento", "cadastro", "emissão", "emissao", "coleta", "liberação",
        "liberacao", "assinatura", "validade", "endereço", "endereco", "telefone", "celular",
        "contato", "documento", "leito", "quarto", "setor", "agenda", "consulta", "receita",
    )

    // Chat line prefix: "12/05/2024 14:32 - " or "[12/05/24, 14:32:05] " (WhatsApp formats).
    private val chatPrefix = Regex(
        """^\s*\[?\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}[,\s]+\d{1,2}:\d{2}(?::\d{2})?\]?\s*[-–—]?\s*""",
    )

    // "Sender Name: message" at line start — up to five words before the colon.
    private val senderLine = Regex(
        """^([\p{Lu}][\p{L}'’.-]{1,29}(?:\s+[\p{L}'’.-]{1,29}){0,4}):\s*(.*)$""",
    )

    fun detect(text: String): List<Detection> {
        if (text.isBlank()) return emptyList()
        val found = ArrayList<Detection>()

        fun addPattern(regex: Regex, category: RedactionCategory, confidence: Confidence, group: Int = 0) {
            regex.findAll(text).forEach { match ->
                val value = (if (group == 0) match.value else match.groupValues.getOrNull(group).orEmpty()).trim()
                if (value.isNotBlank()) found.add(Detection(value, category, confidence))
            }
        }

        addPattern(email, RedactionCategory.CONTACT, Confidence.HIGH)
        addPattern(cnpj, RedactionCategory.DOCUMENT, Confidence.HIGH)
        addPattern(cpf, RedactionCategory.DOCUMENT, Confidence.HIGH)
        addPattern(rg, RedactionCategory.DOCUMENT, Confidence.HIGH, group = 1)
        addPattern(crm, RedactionCategory.DOCUMENT, Confidence.HIGH, group = 1)
        addPattern(crbm, RedactionCategory.DOCUMENT, Confidence.HIGH, group = 1)
        addPattern(recordNumber, RedactionCategory.DOCUMENT, Confidence.HIGH, group = 1)
        addPattern(cns, RedactionCategory.DOCUMENT, Confidence.MEDIUM)
        addPattern(cep, RedactionCategory.ADDRESS, Confidence.MEDIUM)
        addPattern(streetAddress, RedactionCategory.ADDRESS, Confidence.MEDIUM)
        addPattern(phone, RedactionCategory.CONTACT, Confidence.MEDIUM)
        addPattern(numericDate, RedactionCategory.DATE, Confidence.MEDIUM)
        addPattern(writtenDate, RedactionCategory.DATE, Confidence.MEDIUM)
        addPattern(longNumber, RedactionCategory.DOCUMENT, Confidence.LOW)

        // Names are scanned per line: a run must not cross a line break.
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            detectChatSender(line)?.let { found.add(it) }
            found.addAll(detectNames(line))
        }

        // Deduplicate case-insensitively, keeping the strongest confidence per term.
        return found
            .filter { it.term.isNotBlank() }
            .groupBy { it.term.lowercase() }
            .map { (_, group) -> group.minByOrNull { it.confidence.ordinal }!! }
            .sortedWith(compareBy({ it.confidence.ordinal }, { it.term.lowercase() }))
    }

    /**
     * Detects the sender of a chat-style line. A timestamp prefix is decisive; without it the name
     * must contain a dictionary name word, so lab rows ("Hemoglobina: 13,5") and section labels
     * ("Diagnóstico Principal: …") are never auto-selected from this path.
     */
    private fun detectChatSender(line: String): Detection? {
        val prefix = chatPrefix.find(line)
        val rest = (if (prefix != null) line.substring(prefix.value.length) else line).trimStart()
        val match = senderLine.find(rest) ?: return null

        var words = match.groupValues[1].split(Regex("\\s+")).filter { it.isNotBlank() }
        // Strip leading trigger labels ("Mãe da Ana:" → "Ana") and dangling connectors.
        while (words.isNotEmpty() &&
            (clean(words.first()).lowercase() in nameTriggers || words.first().lowercase() in connectors)
        ) {
            words = words.drop(1)
        }
        while (words.isNotEmpty() && words.last().lowercase() in connectors) words = words.dropLast(1)
        if (words.isEmpty()) return null

        val contentWords = words.map { clean(it) }.filter { it.isNotBlank() && it.lowercase() !in connectors }
        if (contentWords.isEmpty()) return null
        if (contentWords.any { word -> !word.all { it.isLetter() || it == '\'' || it == '’' || it == '-' || it == '.' } }) {
            return null
        }
        if (contentWords.all { it.lowercase() in clinicalStopwords || it.lowercase() in labelWords }) return null

        val term = words.joinToString(" ")
        val dated = prefix != null
        // Stopword hits don't count as name evidence ("Nascimento" the surname vs. the form label).
        val hasDictionaryName = contentWords.any {
            it.lowercase() !in clinicalStopwords && BrazilianNames.isKnownNameWord(it)
        }
        val body = match.groupValues[2]
        val proseBody = body.split(Regex("\\s+")).count { token -> token.count { it.isLetter() } >= 2 } >= 2

        val confidence = when {
            dated -> Confidence.HIGH
            hasDictionaryName && (proseBody || contentWords.size >= 2) -> Confidence.HIGH
            hasDictionaryName -> Confidence.MEDIUM
            else -> return null
        }
        return Detection(term, RedactionCategory.NAME, confidence)
    }

    /** Heuristic name detection over a tokenised pass of a single line. */
    private fun detectNames(line: String): List<Detection> {
        val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        val results = ArrayList<Detection>()

        var i = 0
        while (i < tokens.size) {
            val triggered = isTrigger(tokens.getOrNull(i - 1))
            // Build the longest run of name-like words (connectors allowed only between names).
            val words = ArrayList<String>()
            var sawDictionaryName = false
            var j = i
            while (j < tokens.size) {
                val clean = clean(tokens[j])
                val lower = clean.lowercase()
                when {
                    // A trigger label ("Paciente:", "Dr.") or a field label ("CPF") is capitalised
                    // but must not become part of the name itself.
                    lower in nameTriggers || lower in labelWords -> break
                    isNameWord(clean, allowAllCaps = triggered) -> {
                        if (BrazilianNames.isKnownNameWord(clean)) sawDictionaryName = true
                        words.add(clean)
                    }
                    lower in connectors && words.isNotEmpty() &&
                        j + 1 < tokens.size && isNameWord(clean(tokens[j + 1]), allowAllCaps = triggered) ->
                        words.add(clean)
                    else -> break
                }
                // Terminal punctuation on the raw token is a sentence boundary — end the run so
                // "…com Maria. Resultado…" never produces "Maria Resultado".
                if (endsWithBoundary(tokens[j])) {
                    j++
                    break
                }
                j++
            }
            // Trim trailing connectors.
            while (words.isNotEmpty() && words.last().lowercase() in connectors) words.removeAt(words.lastIndex)

            val nameWordCount = words.count { it.lowercase() !in connectors }
            val standaloneDictionaryName = !triggered && nameWordCount == 1 && words.size == 1 &&
                words[0].length >= 3 && BrazilianNames.isFirstName(words[0])
            val minWords = if (triggered || standaloneDictionaryName) 1 else 2

            if (nameWordCount >= minWords && !isAllStopwords(words)) {
                // A name that doubles as a common word ("Clara", "Rosa") carries no signal at a
                // sentence start, where any word is capitalised.
                val sentenceInitial = i == 0 || endsWithBoundary(tokens[i - 1])
                val skipAmbiguous = standaloneDictionaryName && sentenceInitial &&
                    words[0].lowercase() in BrazilianNames.ambiguousNames
                if (!skipAmbiguous) {
                    val term = words.joinToString(" ")
                    val classified = RedactionClassifier.classify(term)
                    val category = when (classified) {
                        RedactionCategory.ORGANIZATION, RedactionCategory.ADDRESS -> classified
                        else -> RedactionCategory.NAME
                    }
                    val confidence = when {
                        triggered && sawDictionaryName -> Confidence.HIGH
                        triggered || sawDictionaryName -> Confidence.MEDIUM
                        else -> Confidence.LOW
                    }
                    results.add(Detection(term, category, confidence))
                }
                i = maxOf(j, i + 1)
            } else {
                i++
            }
        }
        return results
    }

    private fun isTrigger(token: String?): Boolean {
        if (token == null) return false
        return clean(token).lowercase() in nameTriggers
    }

    private fun isNameWord(token: String, allowAllCaps: Boolean): Boolean {
        if (token.length < 2) return false
        val first = token[0]
        if (!first.isLetter() || !first.isUpperCase()) return false
        if (!token.all { it.isLetter() || it == '\'' || it == '-' || it == '’' }) return false
        if (token.any { it.isLowerCase() }) return true
        // ALL-CAPS: lab headers print names in caps ("PACIENTE: MARIA DA SILVA"), but headings and
        // acronyms (CPF, RESULTADO) are also caps. Accept only inside a triggered run or when the
        // word is a known Brazilian name.
        return (allowAllCaps && token.length >= 3) || BrazilianNames.isKnownNameWord(token)
    }

    private fun endsWithBoundary(rawToken: String): Boolean {
        val last = rawToken.lastOrNull() ?: return false
        return last == '.' || last == '!' || last == '?' || last == ';' || last == ':' || last == ','
    }

    private fun isAllStopwords(words: List<String>): Boolean =
        words.filter { it.lowercase() !in connectors }.all { it.lowercase() in clinicalStopwords }

    private fun clean(token: String): String =
        token.trim().trim('.', ',', ';', ':', '(', ')', '[', ']', '"', '\'', '“', '”', '!', '?', '*', '_')
}

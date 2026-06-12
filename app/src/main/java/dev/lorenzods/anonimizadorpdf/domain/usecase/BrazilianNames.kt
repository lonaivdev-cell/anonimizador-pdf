package dev.lorenzods.anonimizadorpdf.domain.usecase

/**
 * Curated dictionaries of common Brazilian given names and surnames (lowercase, with and without
 * accents, since PDF extraction sometimes strips diacritics). Used by [PiiDetector] and
 * [RedactionClassifier] to recognise person names fully offline — including standalone first names
 * in chat records and ALL-CAPS names in lab headers, which capitalization heuristics alone miss.
 *
 * Matching against the dictionary only *raises* confidence and never excludes a candidate, so an
 * uncommon name still surfaces through the capitalization heuristics.
 */
object BrazilianNames {

    val firstNames: Set<String> = setOf(
        // -- most frequent female given names --
        "ana", "maria", "francisca", "antonia", "antônia", "adriana", "juliana", "marcia", "márcia",
        "fernanda", "patricia", "patrícia", "aline", "sandra", "camila", "amanda", "bruna",
        "jessica", "jéssica", "leticia", "letícia", "julia", "júlia", "luciana", "vanessa",
        "mariana", "gabriela", "vera", "vitoria", "vitória", "larissa", "claudia", "cláudia",
        "beatriz", "renata", "eliane", "lucia", "lúcia", "carla", "carolina", "caroline", "simone",
        "luana", "debora", "débora", "sonia", "sônia", "rosa", "cristiane", "cristina", "daniela",
        "daniele", "danielle", "tatiane", "tatiana", "isabela", "isabella", "isabel", "isabelle",
        "sofia", "sophia", "alice", "helena", "valentina", "laura", "heloisa", "heloísa", "livia",
        "lívia", "clara", "cecilia", "cecília", "eloa", "eloá", "lara", "manuela", "manoela",
        "marina", "melissa", "lorena", "rebeca", "rebecca", "raquel", "sara", "sarah", "elisa",
        "eduarda", "catarina", "yasmin", "iasmin", "isadora", "isis", "ísis", "luiza", "luísa",
        "luisa", "giovanna", "giovana", "milena", "regina", "rita", "rosangela", "rosângela",
        "suely", "sueli", "solange", "marlene", "marta", "neusa", "neuza", "raimunda", "terezinha",
        "tereza", "teresa", "vilma", "viviane", "vivian", "wanda", "zilda", "edna", "elaine",
        "eliana", "fatima", "fátima", "gisele", "giselle", "ingrid", "ivone", "janaina", "janaína",
        "joana", "josefa", "kelly", "leila", "lidia", "lídia", "lilian", "lílian", "lourdes",
        "marcela", "margarida", "marilene", "marisa", "mirian", "miriam", "monica", "mônica",
        "natalia", "natália", "nadia", "nádia", "paula", "priscila", "rafaela", "roberta",
        "rosana", "rosane", "sabrina", "samantha", "samanta", "selma", "silvana", "silvia",
        "sílvia", "talita", "tamires", "tania", "tânia", "thais", "thaís", "tais", "taís", "telma",
        "valeria", "valéria", "vania", "vânia", "michele", "michelle", "andreia", "andréia",
        "andrea", "andréa", "angela", "ângela", "angelica", "angélica", "aparecida", "barbara",
        "bárbara", "bianca", "brenda", "carmen", "carmem", "celia", "célia", "cintia", "cíntia",
        "denise", "dulce", "elza", "emilia", "emília", "erica", "érica", "ester", "esther",
        "eunice", "eva", "flavia", "flávia", "gloria", "glória", "iara", "yara", "ines", "inês",
        "iolanda", "irene", "ivana", "ivete", "jaqueline", "jacqueline", "joelma", "josiane",
        "karen", "karina", "carina", "katia", "kátia", "lais", "laís", "lavinia", "lavínia", "lia",
        "ligia", "lígia", "luzia", "madalena", "mara", "mayara", "maiara", "nair", "nazare",
        "nazaré", "neide", "nicole", "pamela", "pâmela", "penha", "poliana", "raissa", "raíssa",
        "ruth", "rute", "sheila", "shirley", "socorro", "stella", "estela", "suelen", "suellen",
        "suzana", "susana", "vanda", "veronica", "verônica", "zelia", "zélia",
        // -- most frequent male given names --
        "joao", "joão", "jose", "josé", "antonio", "antônio", "francisco", "carlos", "paulo",
        "pedro", "lucas", "luiz", "luis", "luís", "marcos", "gabriel", "rafael", "daniel",
        "marcelo", "bruno", "eduardo", "felipe", "filipe", "raimundo", "rodrigo", "manoel",
        "manuel", "mateus", "matheus", "andre", "andré", "fernando", "fabio", "fábio", "leonardo",
        "gustavo", "guilherme", "leandro", "tiago", "thiago", "ricardo", "diego", "vinicius",
        "vinícius", "alex", "jorge", "roberto", "sergio", "sérgio", "julio", "júlio", "alexandre",
        "adriano", "anderson", "arthur", "artur", "augusto", "benedito", "benjamin", "benjamim",
        "bernardo", "breno", "caio", "cesar", "césar", "cicero", "cícero", "claudio", "cláudio",
        "cleber", "kleber", "cleiton", "cristiano", "christian", "cristian", "davi", "david",
        "denilson", "dirceu", "domingos", "douglas", "edgar", "edilson", "edmilson", "edson",
        "edvaldo", "elias", "elton", "emanuel", "emerson", "enzo", "erick", "erik", "eric",
        "ernesto", "evandro", "everton", "ezequiel", "fabiano", "fabricio", "fabrício", "flavio",
        "flávio", "frederico", "geraldo", "gerson", "gilberto", "gilmar", "gilson", "giovanni",
        "giovani", "heitor", "helio", "hélio", "henrique", "hugo", "humberto", "iago", "yago",
        "ian", "yan", "igor", "isaac", "israel", "italo", "ítalo", "ivan", "ivo", "jackson",
        "jaime", "jair", "jairo", "jefferson", "jeferson", "joaquim", "joel", "jonas", "jonathan",
        "jonatan", "josue", "josué", "juliano", "junior", "júnior", "kaique", "kauan", "kauã",
        "kevin", "lazaro", "lázaro", "levi", "lorenzo", "luan", "luciano", "lucio", "lúcio",
        "marcio", "márcio", "mario", "mário", "marlon", "mauricio", "maurício", "mauro", "michel",
        "miguel", "milton", "moacir", "moises", "moisés", "murilo", "nathan", "natan", "nelson",
        "newton", "nicolas", "nilson", "nilton", "norberto", "odair", "orlando", "oscar", "osmar",
        "osvaldo", "oswaldo", "otavio", "otávio", "pablo", "patrick", "pietro", "ramon", "raul",
        "reginaldo", "reinaldo", "renan", "renato", "robson", "rogerio", "rogério", "romario",
        "romário", "ronaldo", "ruan", "rubens", "rui", "ruy", "salomao", "salomão", "samuel",
        "sandro", "saulo", "sebastiao", "sebastião", "severino", "sidnei", "sidney", "silas",
        "silvio", "sílvio", "tadeu", "tarcisio", "tarcísio", "theo", "téo", "thomas", "tomas",
        "tomás", "ulisses", "valdir", "valmir", "valter", "walter", "vagner", "wagner",
        "vanderlei", "wanderson", "vicente", "victor", "vitor", "vladimir", "waldir", "wallace",
        "wellington", "wesley", "willian", "william", "wilson", "yuri",
    )

    val surnames: Set<String> = setOf(
        "silva", "santos", "oliveira", "souza", "sousa", "lima", "pereira", "ferreira", "costa",
        "rodrigues", "almeida", "nascimento", "carvalho", "araujo", "araújo", "gomes", "martins",
        "rocha", "ribeiro", "alves", "monteiro", "mendes", "barros", "freitas", "barbosa", "pinto",
        "moura", "cavalcanti", "cavalcante", "dias", "castro", "campos", "cardoso", "teixeira",
        "correia", "correa", "corrêa", "cunha", "vieira", "moreira", "nunes", "marques", "machado",
        "lopes", "ramos", "fernandes", "andrade", "melo", "mello", "batista", "baptista", "reis",
        "azevedo", "tavares", "farias", "faria", "aguiar", "bezerra", "borges", "brito", "camargo",
        "duarte", "fonseca", "guimaraes", "guimarães", "leal", "macedo", "magalhaes", "magalhães",
        "medeiros", "miranda", "morais", "moraes", "nogueira", "pacheco", "paiva", "peixoto",
        "pires", "queiroz", "rezende", "resende", "sales", "sampaio", "santana", "siqueira",
        "toledo", "torres", "vasconcelos", "viana", "vianna", "xavier", "neves", "antunes",
        "coelho", "dantas", "garcia", "goncalves", "gonçalves", "jesus", "leite", "maia", "neto",
        "prado", "porto", "serra", "silveira", "simoes", "simões", "soares", "valente", "vargas",
    )

    /**
     * Given names that are also ordinary Portuguese words or well-known places. At the start of a
     * sentence the capitalization carries no signal ("Clara melhora do quadro"), so a standalone
     * match there is skipped; mid-sentence or in a name run they count normally.
     */
    val ambiguousNames: Set<String> = setOf(
        "clara", "rosa", "lia", "vitoria", "vitória", "aparecida", "socorro", "penha", "nazare",
        "nazaré", "fatima", "fátima", "lourdes", "gloria", "glória", "graça", "graca", "luz",
        "sol", "mel", "bela", "celeste", "eva",
    )

    fun isFirstName(word: String): Boolean = word.lowercase() in firstNames

    fun isKnownNameWord(word: String): Boolean {
        val lower = word.lowercase()
        return lower in firstNames || lower in surnames
    }
}

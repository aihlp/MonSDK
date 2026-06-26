package com.medmonitoring.core.ai

object AiResponseGrammar {
    val schemaDescription: String = """
        {
          "summary": "insufficient_data|stable|elevated|improving",
          "findingIndex": 0-6,
          "recommendation": "one grounded recommendation sentence",
          "checklist": ["one short goal title"],
          "alert": boolean
        }
    """.trimIndent()

    val gbnf: String = build(allowInsufficientData = true)

    fun build(allowInsufficientData: Boolean): String {
        val summaryRule = if (allowInsufficientData) {
            "\"\\\"insufficient_data\\\"\" | \"\\\"elevated\\\"\" | \"\\\"stable\\\"\" | \"\\\"improving\\\"\""
        } else {
            "\"\\\"elevated\\\"\" | \"\\\"stable\\\"\" | \"\\\"improving\\\"\""
        }
        return """
        root ::= ws "{" ws summary-field ws "," ws finding-field ws "," ws recommendation-field ws "," ws checklist-field ws "," ws alert-field ws "}" ws
        summary-field ::= "\"summary\"" ws ":" ws summary
        finding-field ::= "\"findingIndex\"" ws ":" ws finding-index
        recommendation-field ::= "\"recommendation\"" ws ":" ws recommendation
        checklist-field ::= "\"checklist\"" ws ":" ws checklist
        alert-field ::= "\"alert\"" ws ":" ws boolean
        summary ::= $summaryRule
        finding-index ::= "0" | "1" | "2" | "3" | "4" | "5" | "6"
        recommendation ::= string
        checklist ::= "[" ws checklist-item ws "]"
        checklist-item ::= string
        string ::= "\"" chars "\""
        chars ::= ([^"\\] | escape)*
        escape ::= "\\" (["\\/bfnrt] | "u" hex hex hex hex)
        hex ::= [0-9a-fA-F]
        boolean ::= "true" | "false"
        ws ::= [ \t\n\r]*
        """.trimIndent()
    }
}

object AiQuestionResponseGrammar {
    val gbnf: String = """
        root ::= ws "{" ws answer-field ws "," ws safety-field ws "}" ws
        answer-field ::= "\"answer\"" ws ":" ws string
        safety-field ::= "\"needsClinician\"" ws ":" ws boolean
        string ::= "\"" chars "\""
        chars ::= ([^"\\] | escape)*
        escape ::= "\\" (["\\/bfnrt] | "u" hex hex hex hex)
        hex ::= [0-9a-fA-F]
        boolean ::= "true" | "false"
        ws ::= [ \t\n\r]*
    """.trimIndent()
}

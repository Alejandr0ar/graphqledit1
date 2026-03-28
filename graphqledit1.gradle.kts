description = "graphql edit."

zapAddOn {
    addOnName.set("Graphql Edit")

    manifest {
        author.set("alejandroar")
    }
}

crowdin {
    configuration {
        val resourcesPath = "org/zaproxy/addon/${zapAddOn.addOnId.get()}/resources/"
        tokens.put("%messagesPath%", resourcesPath)
        tokens.put("%helpPath%", resourcesPath)
    }
}

dependencies {
    zapAddOn("commonlib")

    testImplementation(project(":testutils"))
    implementation("com.google.code.gson:gson:2.10.1")
}
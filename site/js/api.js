class Api {
    constructor(tokenMapbox) {
        this.tokenMapbox = tokenMapbox;
    }

    async json(endereco) {
        const resposta = await fetch(endereco);
        if (!resposta.ok) {
            const erro = new Error(`Status ${resposta.status} em ${endereco}`);
            erro.status = resposta.status;
            throw erro;
        }
        return resposta.json();
    }

    pontos() {
        return this.json("/api/pontos");
    }

    contorno(arquivo) {
        return this.json(`data/${arquivo}`);
    }

    catalogo() {
        return this.json("/api/catalogo");
    }

    medicamentos(nome) {
        return this.json(`/api/medicamentos?nome=${encodeURIComponent(nome)}`);
    }

    precos(idMedicamento) {
        return this.json(`/api/precos?medicamento=${idMedicamento}`);
    }

    // geocoding do Mapbox (v6), limitado às duas cidades
    async geocodificar(texto) {
        const url = `https://api.mapbox.com/search/geocode/v6/forward?q=${encodeURIComponent(texto)}`
            + `&country=br&bbox=${BuscaEndereco.CAIXA_CIDADES.join(",")}&proximity=${BuscaEndereco.PROXIMIDADE_CENTRO}`
            + `&language=pt&limit=5&autocomplete=true&access_token=${this.tokenMapbox}`;
        const resposta = await fetch(url);
        if (!resposta.ok) {
            throw new Error(`Status ${resposta.status} no geocoding`);
        }
        const dados = await resposta.json();
        return dados.features || [];
    }
}

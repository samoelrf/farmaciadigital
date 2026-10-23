class RemedioPrecos {
    constructor({ lista, mensagem, api, centro, posicaoUsuario, aoVerNoMapa, aoAviso }) {
        this.lista = lista;
        this.mensagem = mensagem;
        this.api = api;
        this.centro = centro;
        this.posicaoUsuario = posicaoUsuario;
        this.aoVerNoMapa = aoVerNoMapa;
        this.aoAviso = aoAviso;
        this.precos = [];
        this.ordem = "preco";
        this.pedidoAtual = 0;
    }

    static distanciaKm(lat1, lng1, lat2, lng2) {
        const r = Math.PI / 180;
        const a = Math.sin(((lat2 - lat1) * r) / 2) ** 2
            + Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.sin(((lng2 - lng1) * r) / 2) ** 2;
        return 6371 * 2 * Math.asin(Math.sqrt(a));
    }

    // só http e https viram link: nada de javascript: no href
    static linkSeguro(texto) {
        try {
            const url = new URL(texto);
            return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
        } catch {
            return null;
        }
    }

    async buscar(idMedicamento) {
        const pedido = ++this.pedidoAtual;
        this.mensagem.textContent = "Buscando preços…";
        try {
            const precos = await this.api.precos(idMedicamento);
            if (pedido !== this.pedidoAtual) return;
            this.precos = precos;
            this.mostrar();
        } catch (erro) {
            if (pedido !== this.pedidoAtual) return;
            console.error("Não foi possível buscar os preços:", erro);
            this.mensagem.textContent = "";
            this.aoAviso("Não consegui buscar os preços agora. Tente de novo.");
        }
    }

    ordenar(ordem) {
        this.ordem = ordem;
        this.mostrar();
    }

    mostrar() {
        const posicao = this.posicaoUsuario();
        const origem = posicao ?? this.centro;
        const comDistancia = this.precos.map((p) => ({
            ...p,
            distancia: p.latitude === null ? null : RemedioPrecos.distanciaKm(origem.lat, origem.lng, p.latitude, p.longitude),
        }));
        const ordenados = [...comDistancia].sort((a, b) => (this.ordem === "preco"
            ? Number(a.preco) - Number(b.preco) || a.nome.localeCompare(b.nome)
            : (a.distancia === null) - (b.distancia === null) || (a.distancia - b.distancia)));

        this.lista.replaceChildren();
        if (ordenados.length === 0) {
            this.mensagem.textContent = "Nenhuma farmácia tem este remédio em estoque informado.";
            return;
        }
        this.mensagem.textContent = `${ordenados.length} farmácia(s) com este remédio em estoque.`;
        ordenados.forEach((p) => this.lista.appendChild(this.criarCard(p, !posicao)));
    }

    criarCard(p, semPosicao) {
        const item = document.createElement("li");
        item.className = "list-group-item border-0 p-0 mb-3";
        const card = document.createElement("div");
        card.className = "card card-preco h-100";
        const corpo = document.createElement("div");
        corpo.className = "card-body";

        const topo = document.createElement("div");
        topo.className = "d-flex justify-content-between align-items-start gap-2";
        const nome = document.createElement("h3");
        nome.className = "h6 fw-bold mb-1";
        nome.textContent = p.nome;
        const preco = document.createElement("span");
        preco.className = "preco fs-5 fw-bold";
        preco.textContent = Number(p.preco).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
        topo.append(nome, preco);

        const semNoMapa = p.latitude === null;
        const distancia = semNoMapa
            ? "Localização no mapa ainda não cadastrada"
            : `${p.distancia.toFixed(1).replace(".", ",")} km${semPosicao ? " do centro" : ""}`;
        const endereco = document.createElement("p");
        endereco.className = "small text-body-secondary mb-2";
        endereco.textContent = `${p.endereco} · ${distancia}`;

        const selos = document.createElement("div");
        selos.className = "d-flex flex-wrap gap-1 mb-2";
        if (p.exemplo === true) {
            const selo = document.createElement("span");
            selo.className = "badge text-bg-secondary";
            selo.textContent = "Dados de exemplo (fictícios)";
            selos.appendChild(selo);
        }

        const atualizado = document.createElement("p");
        atualizado.className = "small text-body-secondary mb-2";
        const data = new Date(p.atualizadoEm).toLocaleDateString("pt-BR");
        atualizado.textContent = p.informadoPelaFarmacia
            ? `Preço informado pela farmácia em ${data}.`
            : `Preço atualizado em ${data}.`;

        const acoes = document.createElement("div");
        acoes.className = "d-flex flex-wrap gap-2";
        const verNoMapa = document.createElement("button");
        verNoMapa.type = "button";
        verNoMapa.className = "btn btn-sm btn-outline-info rounded-pill";
        verNoMapa.textContent = "Ver no mapa";
        verNoMapa.addEventListener("click", () => this.aoVerNoMapa(p));
        if (!semNoMapa) acoes.appendChild(verNoMapa);
        const url = RemedioPrecos.linkSeguro(p.urlProduto);
        if (url) {
            const link = document.createElement("a");
            link.className = "btn btn-sm btn-outline-info rounded-pill";
            link.href = url;
            link.target = "_blank";
            link.rel = "noopener noreferrer";
            link.textContent = "Ver o produto";
            acoes.appendChild(link);
        }

        corpo.append(topo, endereco, selos, atualizado, acoes);
        card.appendChild(corpo);
        item.appendChild(card);
        return item;
    }
}

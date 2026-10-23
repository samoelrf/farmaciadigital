class Catalogo {
    constructor({ elementos, api }) {
        this.el = elementos;
        this.api = api;
        this.linhas = [];
        this.el.busca.addEventListener("input", () => this.mostrar());
        this.el.filtro.addEventListener("change", () => this.mostrar());
        this.el.limpar.addEventListener("click", () => {
            this.el.busca.value = "";
            this.el.filtro.value = "";
            this.mostrar();
            this.el.busca.focus();
        });
    }

    async carregar() {
        try {
            this.linhas = await this.api.catalogo();
        } catch (erro) {
            if (erro.status === 401 || erro.status === 403) {
                // não é falha: a página é só de administrador
                this.el.avisoErro.classList.replace("alert-danger", "alert-warning");
                this.el.avisoErro.textContent = "Área do administrador: entre com uma conta de administrador. Use o menu Entrar.";
                this.el.avisoErro.hidden = false;
                return;
            }
            console.error("Não foi possível carregar os remédios cadastrados:", erro);
            this.el.avisoErro.textContent = "Não foi possível carregar os remédios cadastrados. Recarregue a página para tentar de novo.";
            this.el.avisoErro.hidden = false;
            return;
        }
        this.preencherFarmacias();
        this.mostrarResumo();
        this.mostrar();
    }

    mostrarResumo() {
        const farmacias = new Set(this.linhas.map((l) => l.farmaciaId));
        const remedios = this.linhas.length;
        const textoRemedios = `${remedios} ${remedios === 1 ? "remédio" : "remédios"}`;
        const textoFarmacias = `${farmacias.size} ${farmacias.size === 1 ? "farmácia" : "farmácias"}`;
        this.el.resumo.textContent = `${textoRemedios} em ${textoFarmacias}`;
        this.el.avisoExemplo.hidden = !this.linhas.some((l) => l.exemplo);
    }

    preencherFarmacias() {
        const nomes = new Map();
        this.linhas.forEach((l) => nomes.set(String(l.farmaciaId), l.farmacia));
        const todas = document.createElement("option");
        todas.value = "";
        todas.textContent = "Todas as farmácias";
        this.el.filtro.replaceChildren(todas);
        [...nomes.entries()]
            .sort((a, b) => a[1].localeCompare(b[1], "pt-BR"))
            .forEach(([id, nome]) => {
                const opcao = document.createElement("option");
                opcao.value = id;
                opcao.textContent = nome;
                this.el.filtro.appendChild(opcao);
            });
    }

    mostrar() {
        const termo = Catalogo.normalizar(this.el.busca.value.trim());
        const farmacia = this.el.filtro.value;
        const filtradas = this.linhas.filter((l) => (!farmacia || String(l.farmaciaId) === farmacia)
            && (!termo || Catalogo.normalizar(l.medicamento).includes(termo) || Catalogo.normalizar(l.farmacia).includes(termo)));

        const grupos = new Map();
        filtradas.forEach((l) => {
            if (!grupos.has(l.farmaciaId)) grupos.set(l.farmaciaId, { farmacia: l, itens: [] });
            grupos.get(l.farmaciaId).itens.push(l);
        });

        this.el.lista.replaceChildren();
        grupos.forEach((grupo) => this.el.lista.appendChild(this.cartaoFarmacia(grupo)));
        if (this.linhas.length === 0) {
            this.el.mensagem.textContent = "Ainda não há remédios cadastrados.";
        } else if (filtradas.length === 0) {
            this.el.mensagem.textContent = "Nenhum remédio ou farmácia combina com o filtro. Tente outro nome ou clique em Limpar filtros.";
        } else {
            this.el.mensagem.textContent = "";
        }
    }

    cartaoFarmacia({ farmacia, itens }) {
        const card = document.createElement("article");
        card.className = "card border-0 shadow-sm rounded-4 mb-4";
        const corpo = document.createElement("div");
        corpo.className = "card-body p-4";

        const titulo = document.createElement("h2");
        titulo.className = "h4 fw-bold mb-1";
        titulo.textContent = farmacia.farmacia;
        const endereco = document.createElement("p");
        endereco.className = "text-body-secondary mb-2";
        endereco.textContent = farmacia.cidade ? `${farmacia.endereco} · ${farmacia.cidade}` : farmacia.endereco;

        const acoes = document.createElement("div");
        acoes.className = "d-flex flex-wrap align-items-center gap-2 mb-3";
        if (farmacia.exemplo) {
            const selo = document.createElement("span");
            selo.className = "badge text-bg-secondary";
            selo.textContent = "Exemplo (fictício)";
            acoes.appendChild(selo);
        }
        const verNoMapa = document.createElement("a");
        verNoMapa.className = "btn btn-outline-info rounded-pill";
        verNoMapa.href = `index.html?ponto=${farmacia.farmaciaId}`;
        verNoMapa.textContent = "Ver no mapa";
        acoes.appendChild(verNoMapa);

        const lista = document.createElement("ul");
        lista.className = "list-group list-group-flush";
        itens.forEach((item) => lista.appendChild(this.linhaRemedio(item)));

        corpo.append(titulo, endereco, acoes, lista);
        card.appendChild(corpo);
        return card;
    }

    linhaRemedio(item) {
        const li = document.createElement("li");
        li.className = "list-group-item px-0 d-flex flex-wrap justify-content-between align-items-center gap-2";

        const esquerda = document.createElement("div");
        const nome = document.createElement("span");
        nome.className = "d-block fw-bold";
        nome.textContent = item.medicamento;
        esquerda.appendChild(nome);
        if (item.atualizadoEm) {
            const data = document.createElement("small");
            data.className = "d-block text-body-secondary";
            data.textContent = `atualizado em ${new Date(item.atualizadoEm).toLocaleDateString("pt-BR")}`;
            esquerda.appendChild(data);
        }

        const direita = document.createElement("div");
        direita.className = "d-flex flex-wrap align-items-center gap-2";
        if (item.quantidade !== null) {
            const estoque = document.createElement("span");
            estoque.className = `badge ${item.quantidade > 0 ? "text-bg-success" : "text-bg-danger"}`;
            estoque.textContent = item.quantidade > 0 ? "Em estoque" : "Sem estoque";
            direita.appendChild(estoque);
        }
        const selo = document.createElement("span");
        selo.className = `badge ${item.informadoPelaFarmacia ? "text-bg-info" : "text-bg-secondary"}`;
        selo.textContent = item.informadoPelaFarmacia ? "informado pela farmácia" : "exemplo";
        direita.appendChild(selo);
        if (item.preco !== null) {
            const preco = document.createElement("span");
            preco.className = "preco fw-bold";
            preco.textContent = Number(item.preco).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
            direita.appendChild(preco);
        }

        li.append(esquerda, direita);
        return li;
    }

    static normalizar(texto) {
        return texto.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
    }
}

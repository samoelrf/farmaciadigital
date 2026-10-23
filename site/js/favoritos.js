class Favoritos {
    constructor() {
        this.logado = false;
        this.conjuntos = { farmacia: new Set(), ubs: new Set(), medicamento: new Set() };
    }

    async iniciar() {
        try {
            const resposta = await fetch("/api/favoritos");
            if (!resposta.ok) return;
            const dados = await resposta.json();
            this.logado = true;
            this.conjuntos.farmacia = new Set(dados.farmacias.map((x) => x.id));
            this.conjuntos.ubs = new Set(dados.ubs.map((x) => x.id));
            this.conjuntos.medicamento = new Set(dados.medicamentos.map((x) => x.id));
        } catch (erro) {
            console.error("Não foi possível carregar os favoritos:", erro);
        }
    }

    ehFavorito(tipo, id) {
        return this.conjuntos[tipo].has(id);
    }

    async alternar(tipo, id) {
        if (!this.logado) return null;
        const marcar = !this.ehFavorito(tipo, id);
        const resposta = await fetch("/api/favoritos", {
            method: "POST",
            body: new URLSearchParams({ tipo, id: String(id), acao: marcar ? "adicionar" : "remover" }),
        });
        if (resposta.status === 401) {
            this.logado = false;
            return null;
        }
        if (!resposta.ok) {
            const dados = await resposta.json().catch(() => ({}));
            throw new Error(dados.erro || "Não foi possível salvar o favorito.");
        }
        if (marcar) this.conjuntos[tipo].add(id); else this.conjuntos[tipo].delete(id);
        return marcar;
    }

    criarBotao(tipo, id, classes = "btn btn-sm btn-outline-primary rounded-pill") {
        const botao = document.createElement("button");
        botao.type = "button";
        botao.className = classes;
        const mostrar = (marcado) => {
            botao.textContent = marcado ? "★ Favorito" : "☆ Favoritar";
            botao.setAttribute("aria-pressed", String(marcado));
        };
        mostrar(this.ehFavorito(tipo, id));
        botao.addEventListener("click", async () => {
            try {
                const marcado = await this.alternar(tipo, id);
                if (marcado === null) {
                    window.location.href = `entrar.html?voltar=${Sessao.caminhoAtual()}`;
                    return;
                }
                mostrar(marcado);
            } catch (erro) {
                console.error(erro);
                botao.textContent = "Não consegui salvar. Tente de novo.";
            }
        });
        return botao;
    }
}

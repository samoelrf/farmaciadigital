const CHAVE_TEMA = "farmaciadigital.tema";

class Tema {
    constructor(botao, opcoes) {
        this.botao = botao;
        this.opcoes = opcoes;
        this.escolha = Tema.lerEscolhaSalva();
        this.modoSistema = window.matchMedia("(prefers-color-scheme: dark)");
        this.modoSistema.addEventListener("change", () => {
            if (this.escolha === "sistema") this.aplicar();
        });
        opcoes.forEach((opcao) => {
            opcao.addEventListener("click", () => this.escolher(opcao.dataset.tema));
        });
        this.aplicar();
    }

    escolher(escolha) {
        this.escolha = escolha;
        Tema.salvarEscolha(escolha);
        this.aplicar();
    }

    // modo privado pode bloquear o armazenamento: começa no claro
    static lerEscolhaSalva() {
        try {
            const salva = localStorage.getItem(CHAVE_TEMA);
            return Tema.NOMES[salva] ? salva : "claro";
        } catch (erro) {
            return "claro";
        }
    }

    static salvarEscolha(escolha) {
        try {
            localStorage.setItem(CHAVE_TEMA, escolha);
        } catch (erro) {
        }
    }

    aplicar() {
        const efetivo = this.escolha === "sistema"
            ? (this.modoSistema.matches ? "escuro" : "claro")
            : this.escolha;
        document.documentElement.setAttribute("data-bs-theme", efetivo === "escuro" ? "dark" : "light");
        this.botao.textContent = `Tema: ${Tema.NOMES[this.escolha]}`;
        this.opcoes.forEach((opcao) => {
            opcao.classList.toggle("active", opcao.dataset.tema === this.escolha);
            opcao.setAttribute("aria-current", String(opcao.dataset.tema === this.escolha));
        });
    }
}

Tema.NOMES = { claro: "claro", escuro: "escuro", sistema: "sistema" };

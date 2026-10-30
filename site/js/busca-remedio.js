class BuscaRemedio {
    static ATRASO_MS = 300;

    constructor({ elementos, api, aoAviso }) {
        this.campo = elementos.campo;
        this.sugestoes = elementos.sugestoes;
        this.escolhidosArea = elementos.escolhidos;
        this.textoErro = elementos.textoErro;
        this.api = api;
        this.aoAviso = aoAviso;
        this.escolhidos = [];
        this.temporizador = null;
        this.pedidoAtual = 0;
        this.campo.addEventListener("input", () => {
            clearTimeout(this.temporizador);
            this.temporizador = setTimeout(() => this.sugerir(), BuscaRemedio.ATRASO_MS);
        });
    }

    async sugerir() {
        const pedido = ++this.pedidoAtual;
        const texto = this.campo.value.trim();
        this.sugestoes.replaceChildren();
        if (!texto) {
            this.sugestoes.hidden = true;
            return;
        }
        try {
            const medicamentos = await this.api.medicamentos(texto);
            if (pedido !== this.pedidoAtual) return;
            medicamentos.forEach((m) => {
                const item = document.createElement("li");
                item.className = "list-group-item p-0";
                const botao = document.createElement("button");
                botao.type = "button";
                botao.className = "list-group-item list-group-item-action border-0";
                botao.textContent = m.nome;
                botao.addEventListener("click", () => this.escolher(m));
                item.appendChild(botao);
                this.sugestoes.appendChild(item);
            });
            this.sugestoes.hidden = this.sugestoes.children.length === 0;
        } catch (erro) {
            if (pedido !== this.pedidoAtual) return;
            console.error("Não foi possível buscar os remédios:", erro);
            this.aoAviso("Não consegui buscar os remédios. Tente de novo.", true);
        }
    }

    escolher(medicamento) {
        this.escolhidos = [medicamento];
        this.campo.value = "";
        this.sugestoes.replaceChildren();
        this.sugestoes.hidden = true;
        this.desenhar();
        Formulario.marcarErro(this.campo, this.textoErro, "");
    }

    remover(id) {
        this.escolhidos = this.escolhidos.filter((e) => e.id !== id);
        this.desenhar();
    }

    desenhar() {
        this.escolhidosArea.replaceChildren();
        this.escolhidos.forEach((m) => {
            const chip = document.createElement("span");
            chip.className = "badge rounded-pill text-bg-info d-inline-flex align-items-center gap-2";
            chip.textContent = m.nome;
            const remover = document.createElement("button");
            remover.type = "button";
            remover.className = "btn-close btn-close-sm";
            remover.setAttribute("aria-label", `Remover ${m.nome}`);
            remover.addEventListener("click", () => this.remover(m.id));
            chip.appendChild(remover);
            this.escolhidosArea.appendChild(chip);
        });
    }

    validar() {
        if (this.escolhidos.length === 0) {
            Formulario.marcarErro(this.campo, this.textoErro, "Escolha um remédio na lista.");
            return false;
        }
        Formulario.marcarErro(this.campo, this.textoErro, "");
        return true;
    }
}

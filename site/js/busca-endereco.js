// só vale se o município for Sorocaba ou Votorantim e o ponto estiver no contorno oficial
class BuscaEndereco {
    static CAIXA_CIDADES = [-47.62, -23.70, -47.25, -23.30];
    static PROXIMIDADE_CENTRO = "-47.4581,-23.5015";
    static MINIMO_CARACTERES = 3;
    static ATRASO_MS = 300;
    static CIDADES_ATENDIDAS = ["sorocaba", "votorantim"];
    static OUTRAS_CIDADES = ["sao paulo", "rio de janeiro", "campinas", "belo horizonte", "curitiba", "ibiuna", "salvador", "recife"];
    static CONTORNO_CAIXA = [{
        geometry: {
            type: "Polygon",
            coordinates: [[
                [-47.62, -23.70], [-47.25, -23.70], [-47.25, -23.30], [-47.62, -23.30], [-47.62, -23.70],
            ]],
        },
    }];

    static normaliza(texto) {
        return [...String(texto ?? "").normalize("NFD")]
            .filter((c) => c < "̀" || c > "ͯ")
            .join("")
            .toLowerCase()
            .trim();
    }

    static municipioDoResultado(feature) {
        const place = feature?.properties?.context?.place;
        return place?.name ?? null;
    }

    static mencionaOutraCidade(texto) {
        const n = BuscaEndereco.normaliza(texto);
        if (n.includes("sorocaba") || n.includes("votorantim")) return false;
        return BuscaEndereco.OUTRAS_CIDADES.some((cidade) => n.includes(cidade));
    }

    static pontoNoAnel(lng, lat, anel) {
        let dentro = false;
        for (let i = 0, j = anel.length - 1; i < anel.length; j = i++) {
            const [xi, yi] = anel[i];
            const [xj, yj] = anel[j];
            if ((yi > lat) !== (yj > lat) && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) {
                dentro = !dentro;
            }
        }
        return dentro;
    }

    static dentroDosContornos(lng, lat, contornos) {
        return contornos.some((f) => {
            const g = f.geometry;
            const poligonos = g.type === "Polygon" ? [g.coordinates] : g.coordinates;
            return poligonos.some((poligono) => BuscaEndereco.pontoNoAnel(lng, lat, poligono[0]));
        });
    }

    static analisarResultados(texto, features, contornos) {
        if (features.length === 0) return { motivo: "vazio", aceitos: [] };
        if (BuscaEndereco.mencionaOutraCidade(texto)) return { motivo: "fora", aceitos: [] };
        const aceitos = features
            .filter((f) => BuscaEndereco.CIDADES_ATENDIDAS.includes(BuscaEndereco.normaliza(BuscaEndereco.municipioDoResultado(f))))
            .filter((f) => BuscaEndereco.dentroDosContornos(f.geometry.coordinates[0], f.geometry.coordinates[1], contornos))
            .map((f) => ({ feature: f, municipio: BuscaEndereco.municipioDoResultado(f) }));
        return { motivo: aceitos.length > 0 ? null : "fora", aceitos };
    }

    static rotulo(feature) {
        const p = feature.properties || {};
        return p.full_address || [p.name, p.place_formatted].filter(Boolean).join(", ") || "Endereço sem nome";
    }

    constructor({ elementos, mapa, api, callbacks }) {
        this.campo = elementos.campo;
        this.lista = elementos.lista;
        this.botaoMarcar = elementos.botaoMarcar;
        this.contagem = elementos.contagem;
        this.textoErro = elementos.textoErro;
        this.mapa = mapa;
        this.api = api;
        this.contornos = [];
        this.callbacks = callbacks;
        this.enderecos = [];
        this.indiceAtivo = -1;
        this.pedidoAtual = 0;
        this.temporizador = null;
        this.marcando = false;
        this.ligarEventos();
    }

    ligarEventos() {
        this.campo.addEventListener("input", () => {
            clearTimeout(this.temporizador);
            const texto = this.campo.value.trim();
            Formulario.marcarErro(this.campo, this.textoErro, "");
            if (texto.length < BuscaEndereco.MINIMO_CARACTERES) {
                this.pedidoAtual++;
                this.fecharLista();
                return;
            }
            this.temporizador = setTimeout(() => this.pesquisar(texto), BuscaEndereco.ATRASO_MS);
        });

        this.campo.addEventListener("keydown", (evento) => this.teclado(evento));

        document.addEventListener("click", (evento) => {
            if (!this.lista.hidden && !evento.target.closest("[data-busca-endereco]")) {
                this.fecharLista();
            }
        });

        if (this.botaoMarcar) {
            this.botaoMarcar.addEventListener("click", () => {
                if (this.marcando) {
                    this.desligarMarcacao();
                } else {
                    this.ligarMarcacao();
                }
            });
        }

        document.addEventListener("keydown", (evento) => {
            if (evento.key === "Escape" && this.marcando) {
                this.desligarMarcacao();
                this.callbacks.aoAviso("");
            }
        });
    }

    async buscar() {
        clearTimeout(this.temporizador);
        if (!this.lista.hidden && this.enderecos.length > 0) {
            this.escolher(this.indiceAtivo >= 0 ? this.indiceAtivo : 0);
            return true;
        }
        const texto = this.campo.value.trim();
        if (texto.length < BuscaEndereco.MINIMO_CARACTERES) {
            Formulario.marcarErro(this.campo, this.textoErro, "Digite pelo menos 3 letras de um endereço, bairro ou CEP.");
            return false;
        }
        return this.pesquisar(texto, true);
    }

    // só a resposta do pedido mais recente vale (pedidoAtual)
    async pesquisar(texto, escolherPrimeiro = false) {
        const pedido = ++this.pedidoAtual;
        try {
            const features = await this.api.geocodificar(texto);
            if (pedido !== this.pedidoAtual) return false;
            const contornos = this.contornos.length > 0 ? this.contornos : BuscaEndereco.CONTORNO_CAIXA;
            const { motivo, aceitos } = BuscaEndereco.analisarResultados(texto, features, contornos);
            if (motivo === "vazio") {
                Formulario.marcarErro(this.campo, this.textoErro, "Nenhum endereço encontrado. Tente com o nome da rua e o número.");
                this.fecharLista();
                return false;
            }
            if (motivo === "fora") {
                Formulario.marcarErro(this.campo, this.textoErro, "Esse endereço fica fora de Sorocaba e Votorantim.");
                this.fecharLista();
                return false;
            }
            if (escolherPrimeiro) {
                this.enderecos = aceitos.map((a) => a.feature);
                this.escolher(0);
            } else {
                this.mostrarSugestoes(aceitos);
            }
            return true;
        } catch (erro) {
            if (pedido !== this.pedidoAtual) return false;
            console.error("Não foi possível buscar o endereço:", erro);
            Formulario.marcarErro(this.campo, this.textoErro, "Não consegui buscar o endereço agora. Tente de novo ou use \"Marcar no mapa\".");
            this.fecharLista();
            return false;
        }
    }

    mostrarSugestoes(aceitos) {
        this.callbacks.aoAviso("", false);
        this.enderecos = aceitos.map((a) => a.feature);
        this.indiceAtivo = -1;
        this.lista.replaceChildren();
        aceitos.forEach((a, i) => {
            const item = document.createElement("li");
            item.id = `opcao-endereco-${i}`;
            item.className = "list-group-item list-group-item-action";
            item.setAttribute("role", "option");
            item.setAttribute("aria-selected", "false");
            const rotulo = document.createElement("span");
            rotulo.textContent = BuscaEndereco.rotulo(a.feature);
            const municipio = document.createElement("small");
            municipio.className = "municipio-endereco d-block text-body-secondary";
            municipio.textContent = a.municipio;
            item.append(rotulo, municipio);
            // mousedown, não click: o campo não perde o foco antes da escolha
            item.addEventListener("mousedown", (evento) => {
                evento.preventDefault();
                this.escolher(i);
            });
            this.lista.appendChild(item);
        });
        this.lista.hidden = false;
        this.campo.setAttribute("aria-expanded", "true");
        this.contagem.textContent = aceitos.length === 1 ? "1 sugestão de endereço" : `${aceitos.length} sugestões de endereço`;
    }

    fecharLista() {
        this.lista.replaceChildren();
        this.lista.hidden = true;
        this.campo.setAttribute("aria-expanded", "false");
        this.campo.setAttribute("aria-activedescendant", "");
        this.contagem.textContent = "";
        this.enderecos = [];
        this.indiceAtivo = -1;
    }

    destacar(indice) {
        this.indiceAtivo = indice;
        [...this.lista.children].forEach((item, i) => {
            item.setAttribute("aria-selected", String(i === indice));
        });
        this.campo.setAttribute("aria-activedescendant", indice >= 0 ? `opcao-endereco-${indice}` : "");
    }

    teclado(evento) {
        const aberta = !this.lista.hidden && this.enderecos.length > 0;
        if (evento.key === "ArrowDown" && aberta) {
            evento.preventDefault();
            this.destacar((this.indiceAtivo + 1) % this.enderecos.length);
        } else if (evento.key === "ArrowUp" && aberta) {
            evento.preventDefault();
            this.destacar(this.indiceAtivo <= 0 ? this.enderecos.length - 1 : this.indiceAtivo - 1);
        } else if (evento.key === "Enter" && aberta) {
            evento.preventDefault();
            this.escolher(this.indiceAtivo >= 0 ? this.indiceAtivo : 0);
        } else if (evento.key === "Escape" && aberta) {
            evento.stopPropagation();
            this.fecharLista();
        }
    }

    escolher(indice) {
        const feature = this.enderecos[indice];
        if (!feature) return;
        const rotulo = BuscaEndereco.rotulo(feature);
        const [lng, lat] = feature.geometry.coordinates;
        this.fecharLista();
        Formulario.marcarErro(this.campo, this.textoErro, "");
        this.campo.value = rotulo;
        this.callbacks.aoEscolher(lat, lng, rotulo);
    }

    setAriaMarcar(valor) {
        if (this.botaoMarcar) this.botaoMarcar.setAttribute("aria-pressed", valor);
    }

    ligarMarcacao() {
        this.callbacks.aoIniciarMarcacao();
        this.fecharLista();
        this.marcando = true;
        document.body.classList.add("marcando");
        this.setAriaMarcar("true");
        this.callbacks.aoAviso("Clique no mapa para marcar sua localização. Esc cancela.", false);
        this.mapa.capturarClique((lngLat) => {
            this.campo.value = "";
            this.desligarMarcacao();
            this.callbacks.aoMarcado(lngLat.lat, lngLat.lng);
        });
    }

    desligarMarcacao() {
        this.mapa.liberarCaptura();
        this.marcando = false;
        document.body.classList.remove("marcando");
        this.setAriaMarcar("false");
        this.callbacks.aoAviso("", false);
    }
}

class Balao {
    static PONTA_PX = 12;
    static MARGEM_TELA_PX = 8;
    static MARGEM_PONTA_PX = 18;
    static DURACAO_ABRIR_MS = 260;
    static DURACAO_FECHAR_MS = 200;
    static CURVA = "cubic-bezier(0.16, 1, 0.3, 1)";
    // espelha o icon-size da camada: se mudar lá, muda aqui
    static ESCALA_FARMACIA = [[9, 0.4], [13, 0.8], [16, 1]];
    static ESCALA_POPULAR = [[9, 0.5], [13, 0.85], [16, 1]];
    static SELETORES_CONTROLES = [".mapboxgl-ctrl-top-right", ".mapboxgl-ctrl-bottom-right", ".mapboxgl-ctrl-bottom-left"];

    constructor({ mapa, fachada, favoritos }) {
        this.mapa = mapa;
        this.fachada = fachada;
        this.favoritos = favoritos;
        this.reduzirMovimento = window.matchMedia("(prefers-reduced-motion: reduce)");
        this.telaPequena = window.matchMedia("(max-width: 767.98px)");
        this.aberto = false;
        this.itens = [];
        this.focoAnterior = null;
        this.ajustouCamera = false;
        this.fotoOcultada = false;
        this.obstaculos = [];
        this.quadroAgendado = false;
        this.criarDom();

        mapa.aoMover(() => this.agendar());
        window.addEventListener("scroll", () => {
            this.atualizarObstaculos();
            this.agendar();
        }, { passive: true });
        window.addEventListener("resize", () => {
            this.atualizarObstaculos();
            this.agendar();
        });
        new ResizeObserver(() => this.agendar()).observe(this.el);
        const observadorObstaculos = new ResizeObserver(() => {
            this.atualizarObstaculos();
            this.agendar();
        });
        document.querySelectorAll("[data-obstaculo]").forEach((el) => observadorObstaculos.observe(el));
        document.addEventListener("keydown", (evento) => {
            if (evento.key === "Escape") this.fechar({ restaurarFoco: true });
        });
    }

    criarDom() {
        const el = document.createElement("section");
        el.className = "cartao";
        el.setAttribute("role", "dialog");
        el.setAttribute("aria-labelledby", "cartao-titulo");
        el.tabIndex = -1;
        el.hidden = true;

        const topo = document.createElement("div");
        topo.className = "cartao-topo";
        const abas = document.createElement("div");
        abas.className = "cartao-abas";
        const fechar = document.createElement("button");
        fechar.type = "button";
        fechar.className = "btn-close cartao-fechar";
        fechar.setAttribute("aria-label", "Fechar");
        fechar.addEventListener("click", () => this.fechar({ restaurarFoco: true }));
        topo.append(abas, fechar);

        const corpo = document.createElement("div");
        corpo.className = "cartao-corpo";

        const ponta = document.createElementNS("http://www.w3.org/2000/svg", "svg");
        ponta.setAttribute("class", "cartao-ponta");
        ponta.setAttribute("viewBox", "0 0 14 12");
        ponta.setAttribute("aria-hidden", "true");
        const triangulo = document.createElementNS("http://www.w3.org/2000/svg", "path");
        triangulo.setAttribute("d", "M-0.5 -1 H14.5 L7 12.5 Z");
        triangulo.setAttribute("class", "cartao-ponta-fundo");
        const contorno = document.createElementNS("http://www.w3.org/2000/svg", "path");
        contorno.setAttribute("d", "M0 0 L7 12 L14 0");
        contorno.setAttribute("class", "cartao-ponta-contorno");
        ponta.append(triangulo, contorno);

        el.append(topo, corpo, ponta);
        document.body.appendChild(el);
        this.el = el;
        this.abas = abas;
        this.corpo = corpo;
        this.fecharBotao = fechar;
    }

    alvoDoPonto(item) {
        // a projeção é em px do mapa; o balão usa a tela, então soma onde o mapa começa
        const mapaNaTela = this.mapa.container.getBoundingClientRect();
        const projetado = this.mapa.projetar(item.coords);
        const p = { x: projetado.x + mapaNaTela.left, y: projetado.y + mapaNaTela.top };
        const zoom = this.mapa.zoom();
        if (item.props.popular === true || item.props.popular === "true") {
            const altura = 40 * Balao.escalaNoZoom(Balao.ESCALA_POPULAR, zoom);
            const cabecaY = p.y - 0.65 * altura;
            const raioCabeca = 0.325 * altura;
            return { x: p.x, y: cabecaY, gap: raioCabeca + 2, xBaixo: p.x, yBaixo: p.y + 2, gapBaixo: 2 };
        }
        const raio = 9 * Balao.escalaNoZoom(Balao.ESCALA_FARMACIA, zoom);
        return { x: p.x, y: p.y, gap: raio + 1, xBaixo: p.x, yBaixo: p.y + raio, gapBaixo: 1 };
    }

    static escalaNoZoom(tabela, zoom) {
        if (zoom <= tabela[0][0]) return tabela[0][1];
        for (let i = 1; i < tabela.length; i++) {
            const [z1, v1] = tabela[i - 1];
            const [z2, v2] = tabela[i];
            if (zoom <= z2) return v1 + ((zoom - z1) / (z2 - z1)) * (v2 - v1);
        }
        return tabela[tabela.length - 1][1];
    }

    atualizarObstaculos() {
        const seletores = ["[data-obstaculo]", ...Balao.SELETORES_CONTROLES];
        this.obstaculos = [];
        seletores.forEach((seletor) => {
            document.querySelectorAll(seletor).forEach((el) => {
                if (el.getClientRects().length > 0) this.obstaculos.push(el.getBoundingClientRect());
            });
        });
    }

    static escolherPosicao(alvo, w, h, obstaculos, vw, vh) {
        const candidatos = [
            { lado: "cima", ax: alvo.x, ay: alvo.y, gap: alvo.gap },
            { lado: "baixo", ax: alvo.xBaixo, ay: alvo.yBaixo, gap: alvo.gapBaixo },
            { lado: "direita", ax: alvo.x, ay: alvo.y, gap: alvo.gap },
            { lado: "esquerda", ax: alvo.x, ay: alvo.y, gap: alvo.gap },
        ];
        const margem = Balao.MARGEM_TELA_PX;
        const ponta = Balao.PONTA_PX;
        const margemPonta = Balao.MARGEM_PONTA_PX;
        const limitar = (v, min, max) => Math.min(Math.max(v, min), max);
        const areaCoberta = (caixa) => obstaculos.reduce((total, r) => {
            const larg = Math.min(caixa.right, r.right) - Math.max(caixa.left, r.left);
            const alt = Math.min(caixa.bottom, r.bottom) - Math.max(caixa.top, r.top);
            return larg > 0 && alt > 0 ? total + larg * alt : total;
        }, 0);

        const avaliados = [];
        for (const c of candidatos) {
            let left;
            let top;
            let tx = null;
            let ty = null;
            if (c.lado === "cima" || c.lado === "baixo") {
                left = limitar(c.ax - w / 2, margem, vw - w - margem);
                top = c.lado === "cima" ? c.ay - c.gap - ponta - h : c.ay + c.gap + ponta;
                tx = c.ax - left;
                if (tx < margemPonta || tx > w - margemPonta) continue;
            } else {
                top = limitar(c.ay - h / 2, margem, vh - h - margem);
                left = c.lado === "direita" ? c.ax + c.gap + ponta : c.ax - c.gap - ponta - w;
                ty = c.ay - top;
                if (ty < margemPonta || ty > h - margemPonta) continue;
            }
            const caixa = { left, top, right: left + w, bottom: top + h };
            const naTela = left >= margem && top >= margem && caixa.right <= vw - margem && caixa.bottom <= vh - margem;
            avaliados.push({ lado: c.lado, left, top, tx, ty, caixa, naTela, cobertura: areaCoberta(caixa) });
        }

        const perfeito = avaliados.find((a) => a.naTela && a.cobertura === 0);
        if (perfeito) return { ...perfeito, cabe: true };

        const dentro = avaliados.filter((a) => a.naTela);
        const escolhido = (dentro.length ? dentro : avaliados).reduce((melhor, a) =>
            !melhor || a.cobertura < melhor.cobertura ? a : melhor, null);
        if (escolhido) return { ...escolhido, cabe: false };

        const left = limitar(alvo.x - w / 2, margem, vw - w - margem);
        const top = limitar(alvo.y - alvo.gap - ponta - h, margem, vh - h - margem);
        return { lado: "cima", left, top, tx: limitar(alvo.x - left, margemPonta, w - margemPonta), ty: null, cabe: false };
    }

    posicionar() {
        if (!this.aberto || this.itens.length === 0) return null;
        if (this.telaPequena.matches) {
            this.el.classList.add("cartao-painel");
            this.el.style.left = "";
            this.el.style.top = "";
            return null;
        }
        this.el.classList.remove("cartao-painel");
        const alvo = this.alvoDoPonto(this.itens[0]);
        const pos = Balao.escolherPosicao(alvo, this.el.offsetWidth, this.el.offsetHeight,
            this.obstaculos, window.innerWidth, window.innerHeight);

        this.el.style.left = `${pos.left}px`;
        this.el.style.top = `${pos.top}px`;
        this.el.dataset.lado = pos.lado;
        if (pos.tx !== null) this.el.style.setProperty("--tx", `${pos.tx}px`);
        if (pos.ty !== null) this.el.style.setProperty("--ty", `${pos.ty}px`);

        if (!pos.cabe) {
            const foto = this.corpo.querySelector(".cartao-foto");
            if (foto && !foto.hidden) {
                foto.hidden = true;
                this.fotoOcultada = true;
                return this.posicionar();
            }
        }
        return pos;
    }

    // arrastar gera muitos eventos: agenda para o próximo quadro
    agendar() {
        if (this.quadroAgendado) return;
        this.quadroAgendado = true;
        requestAnimationFrame(() => {
            this.quadroAgendado = false;
            this.posicionar();
        });
    }

    origemDaAnimacao() {
        const lado = this.el.dataset.lado;
        const tx = parseFloat(this.el.style.getPropertyValue("--tx")) || 0;
        const ty = parseFloat(this.el.style.getPropertyValue("--ty")) || 0;
        if (lado === "cima") return `${tx}px 100%`;
        if (lado === "baixo") return `${tx}px 0`;
        if (lado === "direita") return `0 ${ty}px`;
        return `100% ${ty}px`;
    }

    abrir(itens) {
        if (this.aberto) {
            this.fechar({ restaurarFoco: false, depois: () => this.abrir(itens) });
            return;
        }
        if (!this.el.contains(document.activeElement)) {
            this.focoAnterior = document.activeElement;
        }
        this.itens = itens;
        this.ajustouCamera = false;
        this.fotoOcultada = false;
        this.montarAbas(0);
        this.mostrarItem(0);

        this.el.hidden = false;
        this.aberto = true;
        this.el.getAnimations().forEach((a) => a.cancel());
        this.atualizarObstaculos();
        const pos = this.posicionar();
        if (pos && !pos.cabe && !this.ajustouCamera) {
            this.ajustouCamera = true;
            this.mapa.centralizarParaBalao(itens[0].coords, this.reduzirMovimento.matches ? 0 : 400);
        }
        if (!this.reduzirMovimento.matches && !this.telaPequena.matches) {
            this.el.style.transformOrigin = this.origemDaAnimacao();
            this.el.animate(
                [{ transform: "scale(0.2)", opacity: 0 }, { transform: "scale(1)", opacity: 1 }],
                { duration: Balao.DURACAO_ABRIR_MS, easing: Balao.CURVA }
            );
        }
        this.el.focus({ preventScroll: true });
    }

    fechar({ restaurarFoco = true, depois = null } = {}) {
        if (!this.aberto) {
            if (depois) depois();
            return;
        }
        this.aberto = false;
        this.el.getAnimations().forEach((a) => a.cancel());
        const terminar = () => {
            this.el.hidden = true;
            if (restaurarFoco && this.focoAnterior && document.contains(this.focoAnterior)) {
                this.focoAnterior.focus({ preventScroll: true });
            }
            this.focoAnterior = null;
            if (depois) depois();
        };
        if (this.reduzirMovimento.matches || this.telaPequena.matches) {
            terminar();
            return;
        }
        this.el.style.transformOrigin = this.origemDaAnimacao();
        const animacao = this.el.animate(
            [{ transform: "scale(1)", opacity: 1 }, { transform: "scale(0.2)", opacity: 0 }],
            { duration: Balao.DURACAO_FECHAR_MS, easing: "ease-in", fill: "forwards" }
        );
        animacao.onfinish = terminar;
    }

    montarAbas(selecionado) {
        this.abas.replaceChildren();
        this.abas.hidden = this.itens.length <= 1;
        if (this.itens.length <= 1) return;
        this.itens.forEach((item, i) => {
            const aba = document.createElement("button");
            aba.type = "button";
            aba.className = "btn btn-sm btn-outline-info rounded-pill cartao-aba";
            aba.setAttribute("aria-pressed", String(i === selecionado));
            aba.textContent = item.props.nome;
            aba.addEventListener("click", () => {
                this.montarAbas(i);
                this.mostrarItem(i);
            });
            this.abas.appendChild(aba);
        });
    }

    mostrarItem(i) {
        const item = this.itens[i];
        let conteudo;
        try {
            conteudo = this.criarConteudo(item.props, item.coords);
        } catch (erro) {
            console.error("Não foi possível montar o balão:", erro);
            conteudo = document.createElement("div");
            const titulo = document.createElement("h2");
            titulo.className = "cartao-titulo";
            titulo.id = "cartao-titulo";
            titulo.textContent = item.props.nome;
            conteudo.append(titulo);
        }
        this.corpo.replaceChildren(conteudo);
        this.corpo.scrollTop = 0;
        if (this.itens.length > 1) {
            [...this.abas.children].forEach((aba, j) => aba.setAttribute("aria-pressed", String(j === i)));
        }
        this.posicionar();
    }

    criarConteudo(p, coords) {
        const lat = Number(p.latitude ?? coords[1]);
        const lng = Number(p.longitude ?? coords[0]);
        const ehFarmacia = p.tipo === "farmacia";
        const conteudo = document.createElement("div");
        conteudo.className = "cartao-unidade";

        const foto = this.fachada.criarFoto(lat, lng, p.precisaoPosicao, (figura) => {
            if (this.fotoOcultada) figura.hidden = true;
            this.agendar();
        });
        if (foto) conteudo.appendChild(foto);

        const titulo = document.createElement("h2");
        titulo.className = "cartao-titulo h6 fw-bold";
        titulo.id = "cartao-titulo";
        titulo.textContent = p.nome;
        conteudo.appendChild(titulo);

        const etiquetas = Balao.etiquetas(p, ehFarmacia);
        if (etiquetas.length > 0) {
            const linha = document.createElement("div");
            linha.className = "d-flex flex-wrap gap-1 mb-2";
            etiquetas.forEach(({ texto, classe }) => {
                const etiqueta = document.createElement("span");
                etiqueta.className = `badge ${classe}`;
                etiqueta.textContent = texto;
                linha.appendChild(etiqueta);
            });
            conteudo.appendChild(linha);
        }

        const endereco = document.createElement("p");
        endereco.className = "cartao-endereco small mb-2";
        endereco.textContent = p.cidade ? `${p.endereco} · ${p.cidade}` : p.endereco;
        conteudo.appendChild(endereco);

        conteudo.appendChild(this.criarHorario(p));
        if (p.telefone) {
            const telefone = document.createElement("p");
            telefone.className = "cartao-telefone small mb-2";
            telefone.textContent = `Telefone: ${p.telefone.replace(/;/g, " / ")}`;
            conteudo.appendChild(telefone);
        }
        conteudo.appendChild(this.criarAcoes(p, lat, lng));
        conteudo.appendChild(this.favoritos.criarBotao("farmacia", Number(p.id), "btn btn-sm btn-outline-primary rounded-pill mt-2"));
        return conteudo;
    }

    static etiquetas(p, ehFarmacia) {
        const etiquetas = [];
        if (p.popular === true || p.popular === "true") etiquetas.push({ texto: "Farmácia Popular", classe: "tipo-popular" });
        const ehExemplo = ehFarmacia && (p.exemplo === true || p.exemplo === "true");
        if (ehExemplo) etiquetas.push({ texto: "Dados fictícios", classe: "text-bg-secondary" });
        else if (ehFarmacia && p.tipoFarmacia === "pública") etiquetas.push({ texto: "Farmácia pública", classe: "text-bg-secondary" });
        if (Balao.ehAproximada(p.precisaoPosicao)) {
            etiquetas.push({ texto: "Localização aproximada", classe: "text-bg-warning" });
        }
        return etiquetas;
    }

    static ehAproximada(precisao) {
        return Fachada.PRECISOES_APROXIMADAS.includes(precisao);
    }

    criarHorario(p) {
        const bloco = document.createElement("div");
        bloco.className = "cartao-horario mb-2";

        if (!p.horarioFuncionamento) {
            bloco.appendChild(Balao.texto("p", "small mb-1", "Horário não informado"));
            return bloco;
        }

        let interpretado = { ok: false };
        try {
            interpretado = Horario.interpretar(p.horarioFuncionamento);
        } catch (erro) {
            console.error("Não foi possível ler o horário:", erro);
        }

        if (!interpretado.ok) {
            bloco.appendChild(Balao.texto("p", "small mb-1", p.horarioFuncionamento));
        } else {
            const agora = Horario.agoraNoBrasil();
            const feriadoHoje = Horario.ehFeriadoNacional(agora);
            const codigoHoje = feriadoHoje ? "PH" : Horario.DIAS_ORDEM[agora.dia];
            const aberto = Horario.estaAbertoAgora(interpretado, agora);
            bloco.appendChild(this.criarStatus(interpretado, agora, feriadoHoje, aberto));

            const { dias, feriados } = Horario.agruparDias(interpretado.dias);
            const temFeriados = interpretado.dias.PH !== null && interpretado.dias.PH !== undefined;
            const chips = document.createElement("div");
            chips.className = "cartao-chips d-flex flex-wrap gap-1 mb-1";
            (temFeriados ? dias.concat([feriados]) : dias).forEach((grupo) => {
                const chip = document.createElement("span");
                const hoje = grupo.codigos.includes(codigoHoje);
                chip.className = hoje
                    ? "badge rounded-pill text-bg-info"
                    : "badge rounded-pill bg-body-secondary text-body border";
                chip.textContent = `${grupo.rotulo} ${grupo.texto}`;
                chips.appendChild(chip);
            });
            bloco.appendChild(chips);

            if (interpretado.dias.PH !== null && !feriadoHoje) {
                bloco.appendChild(Balao.texto("p", "small text-body-secondary mb-0", "Em feriados, confirme com a loja"));
            }
        }

        const origem = p.fonteHorario || "OpenStreetMap";
        bloco.appendChild(Balao.texto("p", "cartao-fonte small text-body-secondary mt-1 mb-0", origem === "OpenStreetMap"
            ? "Fonte: OpenStreetMap, colaborativo, pode estar desatualizado"
            : `Fonte: ${origem}`));
        return bloco;
    }

    criarStatus(interpretado, agora, feriadoHoje, aberto) {
        let texto;
        let classe;
        if (feriadoHoje) {
            texto = "Feriado hoje · confirme com a loja";
            classe = "text-bg-secondary";
        } else if (aberto === true) {
            const fecha = Horario.fechamentoDeHoje(interpretado, agora);
            texto = fecha !== null ? `Aberto agora · fecha às ${Balao.horaCurta(fecha)}` : "Aberto agora";
            classe = "text-bg-success";
        } else if (aberto === false) {
            texto = "Fechado agora";
            classe = "text-bg-danger";
        } else {
            texto = "Horário de hoje não informado";
            classe = "text-bg-secondary";
        }
        const selo = document.createElement("p");
        selo.className = "cartao-status mb-1";
        const badge = document.createElement("span");
        badge.className = `badge ${classe}`;
        badge.textContent = texto;
        selo.appendChild(badge);
        return selo;
    }

    static horaCurta(minutos) {
        const m = minutos % 1440;
        return `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
    }

    criarAcoes(p, lat, lng) {
        const acoes = document.createElement("div");
        acoes.className = "cartao-acoes d-flex flex-wrap gap-1 mt-2";
        const link = (texto, href, externo) => {
            const a = document.createElement("a");
            a.className = "btn btn-sm btn-outline-info rounded-pill";
            a.textContent = texto;
            a.href = href;
            if (externo) {
                a.target = "_blank";
                a.rel = "noopener noreferrer";
            }
            return a;
        };
        acoes.appendChild(link("Como chegar", `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`, true));
        if (p.telefone) {
            const primeiro = p.telefone.split(";")[0].split("").filter((c) => c === "+" || (c >= "0" && c <= "9")).join("");
            if (primeiro) acoes.appendChild(link("Ligar", `tel:${primeiro}`, false));
        }
        acoes.appendChild(link("Street View", `https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=${lat},${lng}`, true));
        return acoes;
    }

    static texto(tag, classes, texto) {
        const el = document.createElement(tag);
        el.className = classes;
        el.textContent = texto;
        return el;
    }
}

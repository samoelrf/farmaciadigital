
const CONFIGURACAO = window.CONFIG ?? { MAPBOX_TOKEN: "", STREETVIEW_KEY: "" };
// token público (pk.*), só lê o mapa; restrinja o domínio no painel do Mapbox
const MAPBOX_TOKEN = CONFIGURACAO.MAPBOX_TOKEN ?? "";

const CENTRO_SOROCABA = { lat: -23.5015, lng: -47.4526 };

class App {
    constructor() {
        this.posicao = null;
        this.tiposVisiveis = new Set(["farmacia", "ubs"]);
        this.aviso = $id("aviso");
        this.avisoRemedio = $id("aviso-remedio");
        this.listaLocais = $id("lista-locais");
        this.carregandoMapa = $id("carregando-mapa");
        this.rotuloLocal = $id("rotulo-localizacao");
        this.tema = new Tema($id("botao-tema"), [...document.querySelectorAll("[data-tema]")]);

        const problema = typeof mapboxgl === "undefined"
            ? "O mapa não carregou: verifique a conexão ou se o navegador bloqueou o script do Mapbox."
            : !MAPBOX_TOKEN ? "A chave do mapa não está configurada: crie js/config.js a partir de js/config.exemplo.js."
            : null;
        if (problema) {
            this.carregandoMapa.hidden = true;
            this.mostrarAviso(problema, true);
            return;
        }

        this.api = new Api(MAPBOX_TOKEN);
        this.mapa = new Mapa({
            container: $id("mapa"),
            token: MAPBOX_TOKEN,
            aoAbrirUnidade: (itens) => this.balao.abrir(itens),
            aoClicarVazio: () => this.balao.fechar(),
        });
        this.favoritos = new Favoritos();
        this.favoritos.iniciar();
        this.balao = new Balao({ mapa: this.mapa, fachada: new Fachada(CONFIGURACAO.STREETVIEW_KEY), favoritos: this.favoritos });

        this.busca = new BuscaEndereco({
            elementos: {
                campo: $id("campo-endereco-topo"),
                lista: $id("sugestoes-endereco-topo"),
                botaoMarcar: $id("botao-marcar"),
                contagem: $id("contagem-endereco-topo"),
                textoErro: $id("erro-endereco-topo"),
            },
            mapa: this.mapa,
            api: this.api,
            callbacks: {
                aoEscolher: (lat, lng, rotulo) => {
                    this.definirPosicao(lat, lng, `Localização: ${rotulo}`);
                    this.irParaSecao("buscar");
                },
                aoMarcado: (lat, lng) => this.definirPosicao(lat, lng, "Localização: local marcado no mapa"),
                aoIniciarMarcacao: () => this.balao.fechar({ restaurarFoco: false }),
                aoAviso: (texto, erro) => this.mostrarAviso(texto, erro),
            },
        });

        this.remedios = new BuscaRemedio({
            elementos: {
                campo: $id("campo-remedio"),
                sugestoes: $id("sugestoes"),
                escolhidos: $id("escolhidos"),
                textoErro: $id("erro-remedio"),
            },
            api: this.api,
            aoAviso: (texto) => this.mostrarAvisoRemedio(texto),
        });

        this.precos = new RemedioPrecos({
            lista: $id("lista-precos"),
            mensagem: $id("mensagem-precos"),
            api: this.api,
            centro: CENTRO_SOROCABA,
            posicaoUsuario: () => this.posicao,
            aoVerNoMapa: (p) => this.verNoMapa(p),
            aoAviso: (texto) => this.mostrarAvisoRemedio(texto),
        });

        this.ligarEventos();
        this.carregarDados();
    }

    ligarEventos() {
        document.querySelectorAll("[data-localizacao]").forEach((botao) => {
            botao.addEventListener("click", () => this.usarLocalizacao());
        });

        $id("form-topo").addEventListener("submit", (evento) => {
            evento.preventDefault();
            this.busca.buscar();
        });
        $id("botao-alterar").addEventListener("click", () => this.alterarEndereco());

        document.querySelectorAll("[data-filtro]").forEach((botao) => {
            botao.addEventListener("click", () => this.alternarFiltro(botao.dataset.filtro));
        });

        $id("form-remedio").addEventListener("submit", (evento) => {
            evento.preventDefault();
            this.buscarRemedio();
        });
        $id("select-ordem").addEventListener("change", (evento) => {
            this.precos.ordenar(evento.target.value);
        });

        // no celular o menu fecha ao escolher um link, senão cobre a página
        const menu = $id("menu-principal");
        document.querySelectorAll("#menu-principal .nav-link").forEach((link) => {
            link.addEventListener("click", () => bootstrap.Collapse.getOrCreateInstance(menu, { toggle: false }).hide());
        });

        let temporizador = null;
        window.addEventListener("resize", () => {
            clearTimeout(temporizador);
            temporizador = setTimeout(() => this.mapa.atualizarEspaco(), 150);
        });
    }

    // contornos e pontos: cada um falha sozinho, sem derrubar o outro
    async carregarDados() {
        try {
            await this.mapa.pronto;
        } catch (erro) {
            console.error("Não foi possível carregar o mapa:", erro);
            this.carregandoMapa.hidden = true;
            this.mostrarAviso("Não foi possível carregar o mapa. Verifique a conexão e a chave do mapa (config.js).", true);
            return;
        }

        const [sorocaba, votorantim, pontos] = await Promise.allSettled([
            this.api.contorno("sorocaba.geojson"),
            this.api.contorno("votorantim.geojson"),
            this.api.pontos(),
        ]);

        const cidades = [sorocaba, votorantim];
        const contornos = cidades.filter((r) => r.status === "fulfilled").flatMap((r) => r.value.features);
        cidades.filter((r) => r.status === "rejected").forEach((r) => console.error("Não foi possível carregar um contorno das cidades:", r.reason));
        if (contornos.length > 0) {
            try {
                this.mapa.adicionarContornos(contornos);
            } catch (erro) {
                console.error("Não foi possível desenhar os contornos das cidades:", erro);
            }
            this.busca.contornos = contornos;
        }

        if (pontos.status === "fulfilled") {
            try {
                this.mapa.adicionarPontos(pontos.value);
                this.renderizarLocais();
                this.abrirPontoDaUrl();
            } catch (erro) {
                console.error("Não foi possível desenhar os pontos do mapa:", erro);
                this.mostrarAviso("Não foi possível desenhar as farmácias e UBS no mapa.", true);
            }
        } else {
            console.error("Não foi possível carregar os pontos do mapa:", pontos.reason);
            this.mostrarAviso("Não foi possível carregar as farmácias e UBS. Recarregue a página para tentar de novo.", true);
        }
        this.carregandoMapa.hidden = true;
    }

    alterarEndereco() {
        this.irParaSecao("inicio");
        $id("campo-endereco-topo").focus({ preventScroll: true });
    }

    alternarFiltro(tipo) {
        const novo = new Set(this.tiposVisiveis);
        if (novo.has(tipo)) {
            if (novo.size === 1) return;
            novo.delete(tipo);
        } else {
            novo.add(tipo);
        }
        this.definirFiltro([...novo]);
    }

    definirFiltro(tipos) {
        this.tiposVisiveis = new Set(tipos);
        this.mapa.filtrarTipos(tipos);
        document.querySelectorAll("[data-filtro]").forEach((botao) => {
            const ligado = this.tiposVisiveis.has(botao.dataset.filtro);
            botao.setAttribute("aria-pressed", String(ligado));
            botao.classList.toggle("active", ligado);
            botao.textContent = `${ligado ? "✓ " : ""}${botao.dataset.rotulo}`;
        });
        const tipoDoBalao = this.balao.itens[0]?.props.tipo;
        if (this.balao.aberto && tipoDoBalao && !this.tiposVisiveis.has(tipoDoBalao)) {
            this.balao.fechar({ restaurarFoco: false });
        }
        this.renderizarLocais();
    }

    renderizarLocais() {
        const origem = this.posicao ?? CENTRO_SOROCABA;
        const visiveis = this.mapa.pontos
            .filter((p) => this.tiposVisiveis.has(p.tipo))
            .map((p) => ({ ponto: p, km: RemedioPrecos.distanciaKm(origem.lat, origem.lng, p.latitude, p.longitude) }))
            .sort((a, b) => a.km - b.km);

        this.listaLocais.replaceChildren();
        visiveis.forEach(({ ponto, km }) => this.listaLocais.appendChild(this.criarLocal(ponto, km)));
        const referencia = this.posicao ? "" : " (a partir do centro de Sorocaba)";
        $id("contagem-locais").textContent = `${visiveis.length} local(is), do mais perto para o mais longe${referencia}.`;
    }

    criarLocal(p, km) {
        const item = document.createElement("li");
        item.className = "list-group-item p-0";
        const botao = document.createElement("button");
        botao.type = "button";
        botao.className = "botao-local";

        const nome = document.createElement("span");
        nome.className = "d-block fw-bold";
        nome.textContent = p.nome;

        const linha = document.createElement("span");
        linha.className = "d-flex flex-wrap align-items-center gap-2 mt-1";
        const tipo = document.createElement("span");
        tipo.className = `badge ${p.tipo === "ubs" ? "tipo-ubs" : "tipo-farmacia"}`;
        tipo.textContent = p.tipo === "ubs" ? "UBS" : "Farmácia";
        const distancia = document.createElement("span");
        distancia.className = "fw-bold";
        distancia.textContent = this.formatarDistancia(km);
        linha.append(tipo, distancia);

        const endereco = document.createElement("span");
        endereco.className = "d-block small text-body-secondary mt-1";
        endereco.textContent = p.endereco;

        const selos = document.createElement("span");
        selos.className = "d-flex flex-wrap gap-1 mt-1";
        const aberto = this.estaAbertoAgora(p);
        if (aberto !== null) {
            const situacao = document.createElement("span");
            situacao.className = `badge ${aberto ? "text-bg-success" : "text-bg-danger"}`;
            situacao.textContent = aberto ? "Aberta agora" : "Fechada agora";
            selos.appendChild(situacao);
        }
        if (Balao.ehAproximada(p.precisaoPosicao)) {
            const etiqueta = document.createElement("span");
            etiqueta.className = "badge text-bg-warning";
            etiqueta.textContent = "Localização aproximada";
            selos.appendChild(etiqueta);
        }

        botao.append(nome, linha, endereco, selos);
        botao.addEventListener("click", () => {
            this.mapa.irPara(p.latitude, p.longitude, 16);
            this.balao.abrir([{ props: p, coords: [p.longitude, p.latitude] }]);
        });
        item.appendChild(botao);
        return item;
    }

    formatarDistancia(km) {
        if (km < 1) {
            return `${Math.round((km * 1000) / 10) * 10} m`;
        }
        return `${km.toFixed(1).replace(".", ",")} km`;
    }

    estaAbertoAgora(p) {
        if (!p.horarioFuncionamento) return null;
        try {
            const agora = Horario.agoraNoBrasil();
            if (Horario.ehFeriadoNacional(agora)) return null;
            const interpretado = Horario.interpretar(p.horarioFuncionamento);
            return interpretado.ok ? Horario.estaAbertoAgora(interpretado, agora) : null;
        } catch (erro) {
            return null;
        }
    }

    buscarRemedio() {
        this.mostrarAvisoRemedio("");
        if (!this.remedios.validar()) return;
        const [remedio] = this.remedios.escolhidos;
        this.atualizarFavoritoRemedio(remedio.id);
        this.precos.buscar(remedio.id);
    }

    abrirPontoDaUrl() {
        const params = new URLSearchParams(window.location.search);
        const farmacia = params.get("ponto");
        const valor = farmacia ?? params.get("ubs");
        if (!valor || !/^\d+$/.test(valor)) return;
        const tipo = farmacia !== null ? "farmacia" : "ubs";
        const ponto = this.mapa.pontos.find((p) => p.tipo === tipo && String(p.id) === valor);
        if (ponto) this.verNoMapa(ponto);
    }

    atualizarFavoritoRemedio(idMedicamento) {
        $id("favorito-remedio").replaceChildren(this.favoritos.criarBotao("medicamento", idMedicamento));
    }

    verNoMapa(p) {
        const ponto = this.mapa.pontos.find((x) => Math.abs(x.latitude - p.latitude) < 1e-6
            && Math.abs(x.longitude - p.longitude) < 1e-6);
        if (ponto && !this.tiposVisiveis.has(ponto.tipo)) {
            this.definirFiltro([...this.tiposVisiveis, ponto.tipo]);
        }
        this.irParaSecao("buscar");
        this.mapa.irPara(p.latitude, p.longitude, 16);
        if (ponto) this.balao.abrir([{ props: ponto, coords: [ponto.longitude, ponto.latitude] }]);
    }

    irParaSecao(id) {
        document.getElementById(id).scrollIntoView({ behavior: "smooth", block: "start" });
    }

    // a localização fica só na memória desta página
    definirPosicao(lat, lng, rotulo) {
        this.posicao = { lat, lng };
        this.mapa.mostrarPinUsuario(lat, lng, (lat2, lng2) => this.arrastarPin(lat2, lng2));
        this.mapa.irPara(lat, lng, 14);
        this.rotuloLocal.textContent = rotulo;
        this.mostrarAviso("", false);
        this.renderizarLocais();
    }

    arrastarPin(lat, lng) {
        this.posicao = { lat, lng };
        this.busca.campo.value = "";
        this.busca.fecharLista();
        this.rotuloLocal.textContent = "Localização: ponto ajustado no mapa";
        this.renderizarLocais();
    }

    usarLocalizacao() {
        if (!navigator.geolocation) {
            this.usarCentro("Seu navegador não permite localização. Usando o centro de Sorocaba.", true);
            return;
        }
        this.mostrarAviso("Procurando sua localização...", false);
        navigator.geolocation.getCurrentPosition(
            (p) => this.definirPosicao(p.coords.latitude, p.coords.longitude, "Localização: sua posição atual"),
            () => this.usarCentro("Não foi possível usar sua localização. Usando o centro de Sorocaba.", true)
        );
    }

    usarCentro(texto, erro) {
        this.posicao = null;
        this.mapa.mostrarPinUsuario(CENTRO_SOROCABA.lat, CENTRO_SOROCABA.lng, (lat, lng) => this.arrastarPin(lat, lng));
        this.rotuloLocal.textContent = "Sem localização: usando o centro de Sorocaba";
        this.mostrarAviso(texto, erro);
        this.renderizarLocais();
    }

    mostrarAviso(texto, erro = false) {
        this.aviso.hidden = !texto;
        this.aviso.className = `alert ${erro ? "alert-danger" : "alert-info"} py-2 mb-0 small`;
        this.aviso.textContent = texto;
    }

    mostrarAvisoRemedio(texto) {
        this.avisoRemedio.hidden = !texto;
        this.avisoRemedio.textContent = texto;
    }
}

function $id(id) {
    return document.getElementById(id);
}

const app = new App();

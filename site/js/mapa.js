// atenção: o Mapbox usa [longitude, latitude], nessa ordem
class Mapa {
    static VISTA_INICIAL = [[-47.5703, -23.65], [-47.2968, -23.351]];
    static LIMITE_REGIAO = [[-47.60, -23.68], [-47.27, -23.33]];
    static LIMITE_CLIQUE_PX = 5;

    constructor({ container, token, aoAbrirUnidade, aoClicarVazio }) {
        mapboxgl.accessToken = token;
        this.container = container;
        this.aoAbrirUnidade = aoAbrirUnidade;
        this.aoClicarVazio = aoClicarVazio;
        this.telaPequena = window.matchMedia("(max-width: 991.98px)");
        this.ouvintesMovimento = [];
        this.captura = null;
        this.ponteiroInicio = null;
        this.pinUsuario = null;
        this.pontos = [];
        this.tipos = ["farmacia", "popular"];

        this.mapa = new mapboxgl.Map({
            container,
            style: "mapbox://styles/mapbox/dark-v11",
            projection: "mercator",
            bounds: Mapa.VISTA_INICIAL,
            fitBoundsOptions: { padding: this.paddingDoPainel(), duration: 0 },
            maxPitch: 0,
            attributionControl: { compact: true },
            maxBounds: Mapa.LIMITE_REGIAO,
            minZoom: this.zoomMinimoParaTela(),
            renderWorldCopies: false,
            dragRotate: false,
            pitchWithRotate: false,
            touchPitch: false,
        });
        // zoom com dois dedos não gira: giro não faz sentido aqui
        this.mapa.touchZoomRotate.disableRotation();
        this.mapa.keyboard.disableRotation?.();
        this.mapa.addControl(new mapboxgl.NavigationControl({ showCompass: false }), "top-right");

        this.pronto = new Promise((resolve, reject) => {
            const limite = setTimeout(() => reject(new Error("O mapa demorou demais para carregar")), 15000);
            this.mapa.once("load", () => {
                clearTimeout(limite);
                resolve();
            });
            this.mapa.on("error", (evento) => {
                const status = evento.error?.status;
                if (status === 401 || status === 403) {
                    clearTimeout(limite);
                    reject(new Error(`Mapbox recusou o token (status ${status})`));
                }
            });
        });
        this.pronto.then(() => this.limparEstiloBase(), () => {});

        container.addEventListener("pointerdown", (e) => {
            const r = container.getBoundingClientRect();
            this.ponteiroInicio = { x: e.clientX - r.left, y: e.clientY - r.top };
        });
        this.mapa.on("click", (e) => this.tratarClique(e));
        this.mapa.on("move", () => this.ouvintesMovimento.forEach((fn) => fn()));
    }

    aoMover(fn) {
        this.ouvintesMovimento.push(fn);
    }

    paddingDoPainel() {
        if (this.telaPequena.matches) return { top: 16, bottom: 16, left: 16, right: 16 };
        return { top: 48, bottom: 40, left: 48, right: 48 };
    }

    // o centro da área fica no meio: por isso bottom = altura - 160
    paddingParaBalao() {
        if (!this.telaPequena.matches) return this.paddingDoPainel();
        const altura = this.container.clientHeight;
        const topo = 12;
        return { top: topo, bottom: Math.max(topo, altura - 160), left: 12, right: 12 };
    }

    projetar(coordenada) {
        return this.mapa.project(coordenada);
    }

    zoom() {
        return this.mapa.getZoom();
    }

    irPara(lat, lng, zoom) {
        this.mapa.flyTo({ center: [lng, lat], zoom, padding: this.paddingDoPainel() });
    }

    // ao redimensionar só muda o espaço, a câmera do usuário fica
    atualizarEspaco() {
        this.mapa.setPadding(this.paddingDoPainel());
        this.mapa.setMinZoom(this.zoomMinimoParaTela());
    }

    zoomMinimoParaTela() {
        const [[oeste, sul], [leste, norte]] = Mapa.LIMITE_REGIAO;
        const cosseno = Math.cos((-23.5 * Math.PI) / 180);
        const largura = this.container.clientWidth;
        const altura = this.container.clientHeight;
        const zoomPelaLargura = Math.log2((largura * 360) / (512 * (leste - oeste)));
        const zoomPelaAltura = Math.log2((altura * 360 * cosseno) / (512 * (norte - sul)));
        return Math.max(zoomPelaLargura, zoomPelaAltura, 0);
    }

    centralizarParaBalao(coordenada, duracao) {
        this.mapa.easeTo({ center: coordenada, padding: this.paddingParaBalao(), duration: duracao });
    }

    capturarClique(fn) {
        this.captura = fn;
    }

    liberarCaptura() {
        this.captura = null;
    }

    limparEstiloBase() {
        this.esconderPredios();
        this.suavizarVias();
        this.mapa.getStyle().layers
            .filter((camada) => camada.type === "symbol")
            .forEach((camada) => this.mapa.setLayoutProperty(camada.id, "visibility", "none"));
    }

    esconderPredios() {
        this.mapa.getStyle().layers
            .filter((camada) => camada.type !== "symbol"
                && (camada.id.includes("building") || camada["source-layer"] === "building"))
            .forEach((camada) => this.mapa.setLayoutProperty(camada.id, "visibility", "none"));
    }

    suavizarVias() {
        const padrao = /^(highway_|road_|road-|bridge-|tunnel-)/;
        this.mapa.getStyle().layers
            .filter((camada) => camada.type === "line" && padrao.test(camada.id) && !camada.id.endsWith("-rail"))
            .forEach((camada) => {
                const largura = Mapa.reduzirValor(this.mapa.getPaintProperty(camada.id, "line-width") ?? 1, 0.5);
                const opacidade = Mapa.reduzirValor(this.mapa.getPaintProperty(camada.id, "line-opacity") ?? 1, 0.6);
                if (largura !== null) this.mapa.setPaintProperty(camada.id, "line-width", largura);
                if (opacidade !== null) this.mapa.setPaintProperty(camada.id, "line-opacity", opacidade);
            });
    }

    static reduzirValor(valor, fator) {
        if (typeof valor === "number") return valor * fator;
        if (Array.isArray(valor) && valor[0] === "interpolate") {
            return valor.map((item, i) => (i >= 4 && i % 2 === 0 && typeof item === "number" ? item * fator : item));
        }
        return null;
    }

    adicionarContornos(features) {
        this.mapa.addSource("cidades", { type: "geojson", data: { type: "FeatureCollection", features } });
        this.mapa.addLayer({
            id: "cidades-fundo", type: "fill", source: "cidades",
            paint: { "fill-color": "#8CC8FF", "fill-opacity": 0.07 },
        });
        this.mapa.addLayer({
            id: "cidades-brilho", type: "line", source: "cidades",
            paint: { "line-color": "#8CC8FF", "line-width": 8, "line-blur": 6, "line-opacity": 0.28 },
        });
        this.mapa.addLayer({
            id: "cidades-borda", type: "line", source: "cidades",
            paint: { "line-color": "#8CC8FF", "line-width": 2, "line-opacity": 0.9 },
        });
    }

    static desenharPinPopular() {
        const tela = document.createElement("canvas");
        tela.width = 64;
        tela.height = 80;
        const ctx = tela.getContext("2d");

        ctx.fillStyle = "#7A869A";
        ctx.beginPath();
        ctx.moveTo(32, 78);
        ctx.bezierCurveTo(20, 60, 6, 48, 6, 30);
        ctx.arc(32, 30, 26, Math.PI, 0);
        ctx.bezierCurveTo(58, 48, 44, 60, 32, 78);
        ctx.fill();

        ctx.fillStyle = "#8CC8FF";
        ctx.beginPath();
        ctx.arc(32, 28, 26, 0, Math.PI * 2);
        ctx.fill();

        ctx.strokeStyle = "#1C1E23";
        ctx.lineWidth = 3;
        const r = 5, x = 19, y = 15, s = 26;
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.arcTo(x + s, y, x + s, y + s, r);
        ctx.arcTo(x + s, y + s, x, y + s, r);
        ctx.arcTo(x, y + s, x, y, r);
        ctx.arcTo(x, y, x + s, y, r);
        ctx.closePath();
        ctx.stroke();

        ctx.fillStyle = "#1C1E23";
        ctx.fillRect(29, 20, 6, 18);
        ctx.fillRect(23, 26, 18, 6);
        return ctx.getImageData(0, 0, 64, 80);
    }

    static desenharPontoFarmacia() {
        const tela = document.createElement("canvas");
        tela.width = 36;
        tela.height = 36;
        const ctx = tela.getContext("2d");
        ctx.beginPath();
        ctx.arc(18, 18, 14, 0, Math.PI * 2);
        ctx.fillStyle = "#D93B43";
        ctx.fill();
        ctx.lineWidth = 4;
        ctx.strokeStyle = "rgba(255, 255, 255, 0.9)";
        ctx.stroke();
        return ctx.getImageData(0, 0, 36, 36);
    }

    // popular é a farmácia do Farmácia Popular, as outras são só farmácia
    static categoria(ponto) {
        return ponto.popular === true ? "popular" : "farmacia";
    }

    static montarGeoJson(pontos) {
        return {
            type: "FeatureCollection",
            features: pontos.map((ponto) => ({
                type: "Feature",
                geometry: { type: "Point", coordinates: [ponto.longitude, ponto.latitude] },
                properties: {
                    id: ponto.id,
                    nome: ponto.nome,
                    endereco: ponto.endereco,
                    tipo: ponto.tipo,
                    popular: ponto.popular === true,
                    cidade: ponto.cidade,
                    tipoFarmacia: ponto.tipoFarmacia,
                    precisaoPosicao: ponto.precisaoPosicao,
                    exemplo: ponto.exemplo === true,
                    horarioFuncionamento: ponto.horarioFuncionamento,
                    telefone: ponto.telefone,
                    fonteHorario: ponto.fonteHorario,
                    latitude: ponto.latitude,
                    longitude: ponto.longitude,
                },
            })),
        };
    }

    // farmácias são camadas, não marcadores HTML: assim agrupam em clusters
    adicionarPontos(pontos) {
        this.pontos = pontos;
        this.mapa.addImage("icone-popular", Mapa.desenharPinPopular(), { pixelRatio: 2 });
        this.mapa.addImage("icone-farmacia", Mapa.desenharPontoFarmacia(), { pixelRatio: 2 });
        this.mapa.addSource("pontos", {
            type: "geojson",
            data: Mapa.montarGeoJson(pontos.filter((p) => this.tipos.includes(Mapa.categoria(p)))),
            cluster: true,
            clusterMaxZoom: 9,
            clusterRadius: 40,
        });

        this.mapa.addLayer({
            id: "pontos-clusters",
            type: "circle",
            source: "pontos",
            filter: ["has", "point_count"],
            paint: {
                "circle-color": "#C23038",
                "circle-stroke-color": "#ff8a8f",
                "circle-stroke-width": 1,
                "circle-radius": ["step", ["get", "point_count"], 16, 10, 20, 50, 26],
            },
        });
        this.mapa.addLayer({
            id: "pontos-clusters-contagem",
            type: "symbol",
            source: "pontos",
            filter: ["has", "point_count"],
            layout: {
                "text-field": ["get", "point_count_abbreviated"],
                "text-font": ["DIN Pro Bold", "Arial Unicode MS Bold"],
                "text-size": 14,
            },
            paint: { "text-color": "#fff" },
        });
        this.mapa.addLayer({
            id: "pontos-farmacias",
            type: "symbol",
            source: "pontos",
            filter: ["all", ["!", ["has", "point_count"]], ["!=", ["get", "popular"], true]],
            layout: {
                "icon-image": "icone-farmacia",
                "icon-size": ["interpolate", ["linear"], ["zoom"], 9, 0.4, 13, 0.8, 16, 1],
                "icon-allow-overlap": true,
            },
        });
        this.mapa.addLayer({
            id: "pontos-populares",
            type: "symbol",
            source: "pontos",
            filter: ["all", ["!", ["has", "point_count"]], ["==", ["get", "popular"], true]],
            layout: {
                "icon-image": "icone-popular",
                "icon-anchor": "bottom",
                "icon-size": ["interpolate", ["linear"], ["zoom"], 9, 0.5, 13, 0.85, 16, 1],
                "icon-allow-overlap": true,
            },
        });
        this.mapa.addLayer({
            id: "pontos-nomes",
            type: "symbol",
            source: "pontos",
            minzoom: 15,
            filter: ["!", ["has", "point_count"]],
            layout: {
                "text-field": ["get", "nome"],
                "text-font": ["DIN Pro Medium", "Arial Unicode MS Regular"],
                "text-size": 14,
                "text-offset": [0, 1.2],
                "text-anchor": "top",
                "text-optional": true,
                "text-allow-overlap": false,
            },
            paint: {
                "text-color": "#e9f1f2",
                "text-halo-color": "#02030a",
                "text-halo-width": 1.5,
            },
        });

        ["pontos-clusters", "pontos-farmacias", "pontos-populares"].forEach((camada) => {
            this.mapa.on("mouseenter", camada, () => { this.mapa.getCanvas().style.cursor = "pointer"; });
            this.mapa.on("mouseleave", camada, () => { this.mapa.getCanvas().style.cursor = ""; });
        });
    }

    filtrarTipos(tipos) {
        this.tipos = tipos;
        if (!this.mapa.getSource("pontos")) return;
        const visiveis = this.pontos.filter((p) => tipos.includes(Mapa.categoria(p)));
        this.mapa.getSource("pontos").setData(Mapa.montarGeoJson(visiveis));
    }

    tratarClique(evento) {
        if (this.captura) {
            const fn = this.captura;
            this.captura = null;
            fn(evento.lngLat);
            return;
        }
        if (this.ponteiroInicio && Math.hypot(evento.point.x - this.ponteiroInicio.x, evento.point.y - this.ponteiroInicio.y) > Mapa.LIMITE_CLIQUE_PX) {
            return;
        }
        const { x, y } = evento.point;
        const caixa = [[x - 3, y - 3], [x + 3, y + 3]];

        const clusters = this.mapa.queryRenderedFeatures(caixa, { layers: ["pontos-clusters"] });
        if (clusters.length > 0) {
            this.aproximarCluster(clusters[0]);
            return;
        }

        const unidades = this.mapa.queryRenderedFeatures(caixa, { layers: ["pontos-farmacias", "pontos-populares"] });
        if (unidades.length === 0) {
            this.aoClicarVazio();
            return;
        }
        // a mesma unidade pode vir duas vezes na borda entre blocos do mapa
        const vistos = new Set();
        const itens = [];
        unidades.forEach((u) => {
            const chave = `${u.properties.tipo}|${u.properties.id}`;
            if (vistos.has(chave)) return;
            vistos.add(chave);
            itens.push({ props: u.properties, coords: u.geometry.coordinates });
        });
        this.aoAbrirUnidade(itens);
    }

    aproximarCluster(feature) {
        this.mapa.getSource("pontos").getClusterExpansionZoom(feature.properties.cluster_id, (erro, zoom) => {
            if (erro) {
                console.error("Não foi possível aproximar o grupo de unidades:", erro);
                return;
            }
            this.aoClicarVazio();
            this.mapa.easeTo({ center: feature.geometry.coordinates, zoom, padding: this.paddingDoPainel() });
        });
    }

    mostrarPinUsuario(lat, lng, aoArrastar) {
        if (this.pinUsuario) this.pinUsuario.remove();
        const elemento = document.createElement("div");
        elemento.className = "marcador-usuario";
        elemento.setAttribute("role", "img");
        elemento.setAttribute("aria-label", "Local escolhido");
        elemento.title = "Local escolhido (pode ser arrastado)";
        this.pinUsuario = new mapboxgl.Marker({ element: elemento, draggable: true })
            .setLngLat([lng, lat])
            .addTo(this.mapa);
        this.pinUsuario.on("dragend", () => {
            const ponto = this.pinUsuario.getLngLat();
            aoArrastar(ponto.lat, ponto.lng);
        });
    }
}

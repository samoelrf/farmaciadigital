// posição aproximada ou estimada não mostra foto: pode ser do vizinho
class Fachada {
    static CACHE_MAX = 20;
    static FALHA_EXPIRA_MS = 5 * 60 * 1000;

    constructor(chaveApi) {
        this.chaveApi = chaveApi;
        this.cache = new Map();
    }

    static PRECISOES_EXATAS = ["ponto do endereço", "confirmada"];
    static PRECISOES_APROXIMADAS = ["aproximada (rua)", "aproximada (CEP)", "uma fonte"];

    static bloqueiaFoto(precisao) {
        return !Fachada.PRECISOES_EXATAS.includes(precisao);
    }

    static azimute(lat1, lon1, lat2, lon2) {
        const r = Math.PI / 180;
        const dLon = (lon2 - lon1) * r;
        const y = Math.sin(dLon) * Math.cos(lat2 * r);
        const x = Math.cos(lat1 * r) * Math.sin(lat2 * r) - Math.sin(lat1 * r) * Math.cos(lat2 * r) * Math.cos(dLon);
        return (Math.atan2(y, x) / r + 360) % 360;
    }

    criarFoto(lat, lng, precisao, aoCarregar) {
        if (!this.chaveApi || Fachada.bloqueiaFoto(precisao)) return null;

        const figura = document.createElement("figure");
        figura.className = "cartao-foto mb-2";
        figura.hidden = true;
        const imagem = document.createElement("img");
        imagem.className = "img-fluid rounded w-100";
        imagem.alt = "Fachada do local (Street View)";
        const legenda = document.createElement("figcaption");
        legenda.className = "small text-body-secondary";
        legenda.textContent = "Street View (Google)";
        figura.append(imagem, legenda);

        const chave = `${lng},${lat}`;
        const anterior = this.cache.get(chave);
        if (anterior && anterior.estado === "falhou" && Date.now() - anterior.quando < Fachada.FALHA_EXPIRA_MS) {
            return null;
        }

        const mostrar = (entrada) => {
            imagem.onload = () => {
                figura.hidden = false;
                legenda.textContent = `Street View (Google) · foto de ${entrada.ano}`;
                aoCarregar(figura);
            };
            imagem.src = entrada.url;
        };

        if (anterior && anterior.estado === "ok") {
            this.guardar(chave, anterior);
            mostrar(anterior);
            return figura;
        }

        this.buscar(lat, lng)
            .then((entrada) => {
                this.guardar(chave, entrada || { estado: "falhou", quando: Date.now() });
                if (!entrada) {
                    figura.remove();
                    return;
                }
                mostrar(entrada);
            })
            .catch(() => {
                this.guardar(chave, { estado: "falhou", quando: Date.now() });
                figura.remove();
            });
        return figura;
    }

    // metadados não gastam cota de imagem
    async buscar(lat, lng) {
        const chave = encodeURIComponent(this.chaveApi);
        const meta = await fetch(`https://maps.googleapis.com/maps/api/streetview/metadata?location=${lat},${lng}&radius=50&source=outdoor&key=${chave}`)
            .then((r) => r.json());
        if (!meta || meta.status !== "OK") return null;

        const pano = meta.location;
        const rumo = Math.round(Fachada.azimute(pano.lat, pano.lng, lat, lng));
        return {
            estado: "ok",
            quando: Date.now(),
            ano: meta.date ? String(meta.date).slice(0, 4) : "data desconhecida",
            url: `https://maps.googleapis.com/maps/api/streetview?size=480x220&location=${pano.lat},${pano.lng}&heading=${rumo}&pitch=0&fov=80&source=outdoor&key=${chave}`,
        };
    }

    guardar(chave, entrada) {
        this.cache.delete(chave);
        this.cache.set(chave, entrada);
        if (this.cache.size > Fachada.CACHE_MAX) {
            this.cache.delete(this.cache.keys().next().value);
        }
    }
}

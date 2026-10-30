"use strict";
// Cadastro da farmácia: dados, endereço de Sorocaba ou Votorantim e localização no mapa.
// O servidor confere tudo de novo (cidade, caixa do mapa, CNPJ); esta página só guia a pessoa.
// Todo texto de fora entra por textContent.

const $ = (id) => document.getElementById(id);
const ACURACIA_EXATA = ["rooftop", "parcel", "point"];

const estado = {
    cnpjEstado: null, // nova | base | em_analise | indisponivel
    contornos: [],
    pedidoCnpj: 0,
    pedidoLocal: 0,
};

const api = new Api((window.CONFIG && window.CONFIG.MAPBOX_TOKEN) || "");

function mostrarErro(texto) {
    $("aviso").textContent = texto;
    $("aviso").hidden = false;
    window.scrollTo({ top: 0, behavior: "smooth" });
}

function soNumeros(texto) {
    return texto.replace(/\D/g, "");
}

// ----- situação do pedido atual -----

const TEXTOS = {
    pendente: "Seu pedido está em análise pela nossa equipe.",
    recusado: "Seu último pedido foi recusado. Você pode corrigir os dados e enviar de novo.",
};

function mostrarSituacao(acesso) {
    const situacao = $("situacao");
    const form = $("form-acesso");
    $("link-painel").hidden = true;
    $("acoes-situacao").hidden = true;
    form.hidden = false;

    if (acesso.status === "aprovado") {
        situacao.textContent = `Acesso aprovado à farmácia ${acesso.farmacia}. Abra o painel da farmácia no menu da sua conta.`;
        form.hidden = true;
    } else if (acesso.status === "pendente") {
        situacao.textContent = acesso.painel
            ? `Cadastro de ${acesso.farmacia} em análise. Você já pode lançar o estoque e os serviços; a farmácia aparece no mapa depois da aprovação.`
            : `${TEXTOS.pendente} O painel abre depois da aprovação.`;
        form.hidden = true;
    } else if (acesso.status === "recusado") {
        situacao.textContent = TEXTOS.recusado;
    } else {
        situacao.hidden = true;
        return;
    }
    situacao.hidden = false;
    if (acesso.painel) {
        $("link-painel").hidden = false;
        $("acoes-situacao").hidden = false;
    }
}

// ----- CNPJ: já está na base? -----

function mostrarEstadoCnpj(tipo, texto) {
    const caixa = $("estado-cnpj");
    caixa.className = `alert mt-2 mb-0 alert-${tipo}`;
    caixa.textContent = texto;
    caixa.hidden = false;
}

function aplicarEstadoCnpj(resposta) {
    estado.cnpjEstado = resposta.estado;
    const novo = resposta.estado === "nova";
    $("bloco-endereco").hidden = !novo;
    $("botao-enviar").disabled = resposta.estado === "em_analise" || resposta.estado === "indisponivel";
    if (novo) {
        $("estado-cnpj").hidden = true;
    } else if (resposta.estado === "base") {
        mostrarEstadoCnpj("info", `Esta farmácia já está na nossa base: ${resposta.nome}. Seu pedido será analisado pela nossa equipe e o painel abre depois da aprovação.`);
    } else if (resposta.estado === "em_analise") {
        mostrarEstadoCnpj("warning", "Este CNPJ já tem um cadastro aguardando aprovação.");
    } else {
        mostrarEstadoCnpj("warning", "Este CNPJ já foi usado em outro cadastro. Fale com a nossa equipe.");
    }
}

async function verificarCnpj() {
    const cnpj = soNumeros($("cnpj").value);
    const meuPedido = ++estado.pedidoCnpj;
    if (cnpj.length !== 14) {
        estado.cnpjEstado = null;
        $("bloco-endereco").hidden = true;
        $("estado-cnpj").hidden = true;
        $("botao-enviar").disabled = false;
        return;
    }
    const resposta = await fetch(`/api/acesso-farmacia/verificar?cnpj=${cnpj}`);
    if (meuPedido !== estado.pedidoCnpj) return; // chegou resposta de um CNPJ antigo
    const dados = await resposta.json().catch(() => ({}));
    if (!resposta.ok) {
        estado.cnpjEstado = null;
        $("bloco-endereco").hidden = true;
        mostrarEstadoCnpj("danger", dados.erro || "Não consegui conferir o CNPJ agora.");
        return;
    }
    aplicarEstadoCnpj(dados);
}

// ----- localização no mapa -----

function limparLocalizacao() {
    estado.pedidoLocal++;
    ["latitude", "longitude", "precisao"].forEach((id) => { $(id).value = ""; });
    $("local-escolhido").hidden = true;
    $("opcoes-local").hidden = true;
    $("opcoes-local").replaceChildren();
    $("erro-local").hidden = true;
}

function mostrarErroLocal(texto) {
    $("erro-local").textContent = texto;
    $("erro-local").hidden = false;
    $("opcoes-local").hidden = true;
}

function escolherLocal(feature, rotulo) {
    const [lng, lat] = feature.geometry.coordinates;
    const acuracia = feature.properties && feature.properties.coordinates && feature.properties.coordinates.accuracy;
    $("latitude").value = lat.toFixed(6);
    $("longitude").value = lng.toFixed(6);
    $("precisao").value = ACURACIA_EXATA.includes(acuracia) ? "exata" : "aproximada";
    $("texto-local").textContent = rotulo;
    $("link-local").href = `https://www.openstreetmap.org/?mlat=${lat.toFixed(6)}&mlon=${lng.toFixed(6)}#map=18/${lat.toFixed(6)}/${lng.toFixed(6)}`;
    $("local-escolhido").hidden = false;
    $("opcoes-local").hidden = true;
    $("erro-local").hidden = true;
}

function mostrarOpcoes(aceitos) {
    const lista = $("opcoes-local");
    lista.replaceChildren(...aceitos.map((a) => {
        const rotulo = BuscaEndereco.rotulo(a.feature);
        const item = document.createElement("li");
        item.className = "list-group-item list-group-item-action";
        item.style.cursor = "pointer";
        item.tabIndex = 0;
        item.setAttribute("role", "button");
        const texto = document.createElement("span");
        texto.textContent = rotulo;
        const cidade = document.createElement("small");
        cidade.className = "d-block text-body-secondary";
        cidade.textContent = a.municipio;
        item.append(texto, cidade);
        const escolher = () => escolherLocal(a.feature, rotulo);
        item.addEventListener("click", escolher);
        item.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); escolher(); } });
        return item;
    }));
    const titulo = document.createElement("li");
    titulo.className = "list-group-item small text-body-secondary";
    titulo.textContent = aceitos.length === 1 ? "Encontramos este endereço. Clique para marcar:" : "Escolha o endereço certo:";
    lista.prepend(titulo);
    lista.hidden = false;
    $("erro-local").hidden = true;
}

async function localizar() {
    limparLocalizacao();
    const cidade = $("cidade").value;
    const rua = $("endereco").value.trim();
    const bairro = $("bairro").value.trim();
    if (!cidade) return mostrarErroLocal("Escolha a cidade: Sorocaba ou Votorantim.");
    if (rua.length < 5) return mostrarErroLocal("Digite a rua e o número da farmácia.");
    if (!(window.CONFIG && window.CONFIG.MAPBOX_TOKEN)) {
        return mostrarErroLocal("O mapa não está configurado neste site (falta a chave do Mapbox). Avise o administrador.");
    }
    const meuPedido = ++estado.pedidoLocal;
    const cep = soNumeros($("cep").value);
    const consulta = [rua, bairro, cidade, "SP", cep.length === 8 ? cep : ""].filter(Boolean).join(", ");
    $("botao-localizar").disabled = true;
    try {
        const features = await api.geocodificar(consulta);
        if (meuPedido !== estado.pedidoLocal) return;
        const contornos = estado.contornos.length > 0 ? estado.contornos : BuscaEndereco.CONTORNO_CAIXA;
        const { motivo, aceitos } = BuscaEndereco.analisarResultados(consulta, features, contornos);
        if (motivo === "vazio") return mostrarErroLocal("Nenhum endereço encontrado. Confira a rua, o número e o bairro.");
        // o ponto precisa estar na cidade escolhida, não só em uma das duas
        const daCidade = aceitos.filter((a) => BuscaEndereco.normaliza(a.municipio) === BuscaEndereco.normaliza(cidade));
        if (daCidade.length === 0) {
            return mostrarErroLocal(motivo === "fora"
                ? "Esse endereço fica fora de Sorocaba e Votorantim."
                : `Achamos o endereço em outra cidade, não em ${cidade}. Confira a cidade escolhida.`);
        }
        mostrarOpcoes(daCidade);
    } catch (erro) {
        if (meuPedido !== estado.pedidoLocal) return;
        console.error("Não foi possível localizar o endereço:", erro);
        mostrarErroLocal("Não consegui buscar o endereço agora. Tente de novo em instantes.");
    } finally {
        $("botao-localizar").disabled = false;
    }
}

// ----- envio -----

async function enviar(evento) {
    evento.preventDefault();
    $("aviso").hidden = true;
    const form = evento.target;
    const cnpj = soNumeros($("cnpj").value);
    if (cnpj.length !== 14) return mostrarErro("Digite o CNPJ com 14 números.");
    if (soNumeros($("telefone").value).length < 10) return mostrarErro("Digite o telefone com DDD.");
    if (!estado.cnpjEstado) await verificarCnpj();
    if (estado.cnpjEstado === "nova") {
        if (!$("nome").value.trim()) return mostrarErro("Informe o nome da farmácia.");
        if (!$("cidade").value) return mostrarErro("Escolha a cidade: Sorocaba ou Votorantim.");
        if ($("endereco").value.trim().length < 5) return mostrarErro("Informe a rua e o número.");
        if (!$("bairro").value.trim()) return mostrarErro("Informe o bairro.");
        if (!$("latitude").value) return mostrarErro("Clique em \"Localizar no mapa\" e escolha o endereço da farmácia.");
    }
    $("botao-enviar").disabled = true;
    try {
        const resposta = await fetch("/api/acesso-farmacia", { method: "POST", body: new URLSearchParams(new FormData(form)) });
        const dados = await resposta.json().catch(() => ({}));
        if (resposta.status === 201) {
            const nomeEnviado = $("nome").value.trim();
            form.reset();
            limparLocalizacao();
            $("bloco-endereco").hidden = true;
            $("estado-cnpj").hidden = true;
            estado.cnpjEstado = null;
            const acesso = { status: "pendente", farmacia: nomeEnviado, painel: dados.painel === true };
            // recarrega do servidor para mostrar o nome certo
            const atual = await fetch("/api/acesso-farmacia").then((r) => r.json()).catch(() => acesso);
            mostrarSituacao(atual);
            window.scrollTo({ top: 0, behavior: "smooth" });
            return;
        }
        mostrarErro(dados.erro || "Não consegui enviar o cadastro agora. Tente de novo.");
    } finally {
        $("botao-enviar").disabled = estado.cnpjEstado === "em_analise" || estado.cnpjEstado === "indisponivel";
    }
}

// ----- início -----

async function carregarContornos() {
    const resultados = await Promise.allSettled([api.contorno("sorocaba.geojson"), api.contorno("votorantim.geojson")]);
    estado.contornos = resultados.filter((r) => r.status === "fulfilled").flatMap((r) => r.value.features);
}

async function iniciar() {
    const resposta = await fetch("/api/acesso-farmacia");
    if (resposta.status === 401) {
        $("sem-entrar").hidden = false;
        return;
    }
    mostrarSituacao(await resposta.json());
    carregarContornos();
}

$("cnpj").addEventListener("input", verificarCnpj);
["cidade", "endereco", "bairro", "cep"].forEach((id) => $(id).addEventListener("input", limparLocalizacao));
$("botao-localizar").addEventListener("click", localizar);
$("form-acesso").addEventListener("submit", enviar);

iniciar();

"use strict";
// Painel da farmácia: estoque por lote e serviços gratuitos.
// Todo texto vindo do servidor entra na página por textContent; o id da farmácia
// vem do servidor (pela sessão), nunca desta página.

const $ = (id) => document.getElementById(id);
const TIPOS = { referencia: "Referência", generico: "Genérico", similar: "Similar" };

const estado = {
    remedios: [],
    servicos: [],
    editandoServico: null, // id do serviço em edição
    editandoLote: false,
};

// ----- utilidades -----

function el(tag, classes, texto) {
    const elemento = document.createElement(tag);
    if (classes) elemento.className = classes;
    if (texto !== undefined && texto !== null) elemento.textContent = texto;
    return elemento;
}

function badge(classes, texto) {
    return el("span", `badge ${classes}`, texto);
}

function botao(classes, texto, aoClicar) {
    const b = el("button", `btn btn-sm rounded-pill ${classes}`, texto);
    b.type = "button";
    b.addEventListener("click", aoClicar);
    return b;
}

function dinheiro(valor) {
    return valor === null || valor === undefined
        ? "—"
        : Number(valor).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function dataBr(iso) {
    return new Date(`${iso}T00:00:00`).toLocaleDateString("pt-BR");
}

function semAcento(texto) {
    return texto.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
}

function mostrarAviso(texto) {
    $("aviso-ok").hidden = true;
    $("aviso").textContent = texto;
    $("aviso").hidden = false;
    window.scrollTo({ top: 0, behavior: "smooth" });
}

let temporizadorOk = null;
function mostrarOk(texto) {
    $("aviso").hidden = true;
    $("aviso-ok").textContent = texto;
    $("aviso-ok").hidden = false;
    clearTimeout(temporizadorOk);
    temporizadorOk = setTimeout(() => { $("aviso-ok").hidden = true; }, 3500);
}

// POST de um formulário; devolve true se deu certo, senão mostra o erro do servidor
async function enviar(url, campos) {
    $("aviso").hidden = true;
    let resposta;
    try {
        resposta = await fetch(url, { method: "POST", body: new URLSearchParams(campos) });
    } catch (erro) {
        mostrarAviso("Sem conexão com o servidor. Tente de novo.");
        return false;
    }
    const dados = await resposta.json().catch(() => ({}));
    if (!resposta.ok) {
        mostrarAviso(dados.erro || "Não consegui salvar agora. Tente de novo.");
        return false;
    }
    return true;
}

// ----- estoque: leitura e desenho -----

async function carregarEstoque() {
    const resposta = await fetch("/api/painel/estoque");
    if (!resposta.ok) {
        mostrarAviso("Não consegui carregar o estoque. Recarregue a página.");
        return;
    }
    const dados = await resposta.json();
    estado.remedios = dados.remedios;
    desenharResumo(dados.resumo);
    desenharEstoque();
}

function desenharResumo(resumo) {
    const caixa = $("resumo-estoque");
    caixa.replaceChildren();
    if (resumo.lotesVencidos > 0) {
        caixa.append(badge("text-bg-danger fs-6", `${resumo.lotesVencidos} lote(s) vencido(s) com saldo`));
    }
    if (resumo.lotesVencendo > 0) {
        caixa.append(badge("text-bg-warning fs-6", `${resumo.lotesVencendo} lote(s) vencem em até ${resumo.diasAviso} dias`));
    }
    if (resumo.abaixoDoMinimo > 0) {
        caixa.append(badge("text-bg-danger fs-6", `${resumo.abaixoDoMinimo} remédio(s) abaixo do mínimo`));
    }
}

function badgeDoLote(lote) {
    if (lote.situacao === "vencido") return badge("text-bg-danger", "Vencido");
    if (lote.situacao === "vence_em_breve") {
        const dias = lote.diasParaVencer;
        return badge("text-bg-warning", dias === 0 ? "Vence hoje" : `Vence em ${dias} dia${dias === 1 ? "" : "s"}`);
    }
    return badge("text-bg-success", "No prazo");
}

function criarTabelaDeLotes(remedio) {
    const envoltorio = el("div", "table-responsive");
    const tabela = el("table", "table table-sm align-middle mb-0");
    const cabecalho = el("thead");
    const linhaCab = el("tr");
    ["Lote", "Validade", "Qtd.", "Venda", "Custo", "Fornecedor", ""].forEach((titulo) => linhaCab.append(el("th", "small text-body-secondary", titulo)));
    cabecalho.append(linhaCab);
    const corpo = el("tbody");
    remedio.lotes.forEach((lote) => {
        const linha = el("tr");
        const validade = el("td");
        validade.append(el("span", "me-2", dataBr(lote.validade)), badgeDoLote(lote));
        const acoes = el("td", "text-end text-nowrap");
        acoes.append(
            botao("btn-outline-primary me-1", "Alterar", () => editarLote(remedio, lote)),
            botao("btn-outline-danger", "Remover", () => removerLote(remedio, lote)),
        );
        linha.append(
            el("td", "fw-semibold", lote.lote),
            validade,
            el("td", null, String(lote.quantidade)),
            el("td", null, dinheiro(lote.precoVenda)),
            el("td", null, dinheiro(lote.precoCusto)),
            el("td", null, lote.fornecedor || "—"),
            acoes,
        );
        corpo.append(linha);
    });
    tabela.append(cabecalho, corpo);
    envoltorio.append(tabela);
    return envoltorio;
}

function criarCartaoDoRemedio(remedio) {
    const cartao = el("div", "card border-0 shadow-sm rounded-4 mb-3");
    const corpo = el("div", "card-body");

    const topo = el("div", "d-flex flex-wrap justify-content-between align-items-start gap-2 mb-2");
    const titulo = el("div");
    titulo.append(el("h3", "h6 fw-bold mb-0", remedio.nome));
    if (remedio.apresentacao) titulo.append(el("small", "d-block text-body-secondary", remedio.apresentacao));
    const marcas = el("div", "d-flex flex-wrap gap-1 mt-1");
    if (remedio.exigeReceita) marcas.append(badge("text-bg-secondary", "Exige receita"));
    if (remedio.tipo && TIPOS[remedio.tipo]) marcas.append(badge("text-bg-light border", TIPOS[remedio.tipo]));
    if (remedio.abaixoDoMinimo) marcas.append(badge("text-bg-danger", "Abaixo do mínimo"));
    if (marcas.children.length > 0) titulo.append(marcas);

    const resumo = el("div", "text-end");
    resumo.append(
        el("div", "fw-bold fs-5", `${remedio.quantidadeValida} un.`),
        el("div", "small text-body-secondary", `mínimo ${remedio.minimo} · preço ${dinheiro(remedio.precoAtual)}`),
    );
    topo.append(titulo, resumo);

    const acoes = el("div", "d-flex flex-wrap gap-2 mb-3");
    acoes.append(
        botao("btn-outline-primary", "Novo lote", () => novoLoteDe(remedio)),
        botao("btn-outline-secondary", "Estoque mínimo", () => definirMinimo(remedio)),
    );

    corpo.append(topo, acoes);
    if (remedio.semLote) {
        corpo.append(el("p", "small text-body-secondary mb-0",
            "Sem lote cadastrado: esta quantidade foi registrada antes do controle por lote. Cadastre um lote para controlar a validade."));
    } else {
        corpo.append(criarTabelaDeLotes(remedio));
    }
    cartao.append(corpo);
    return cartao;
}

function desenharEstoque() {
    const filtro = semAcento($("filtro-estoque").value.trim());
    const visiveis = estado.remedios.filter((r) => semAcento(`${r.nome} ${r.apresentacao || ""}`).includes(filtro));
    $("lista-estoque").replaceChildren(...visiveis.map(criarCartaoDoRemedio));
    $("vazio-estoque").textContent = estado.remedios.length === 0 ? "Nenhum remédio cadastrado ainda." : "Nenhum remédio com esse nome.";
    $("vazio-estoque").hidden = visiveis.length > 0;
}

// ----- estoque: formulário -----

function mostrarBlocoNovo(mostrar) {
    $("bloco-novo").hidden = !mostrar;
    $("bloco-escolha").hidden = mostrar;
    $("novo-nome").required = mostrar;
}

function limparFormularioDoLote() {
    $("form-lote").reset();
    $("medicamento").replaceChildren(new Option("Busque o remédio acima", ""));
    mostrarBlocoNovo(false);
    ["busca-remedio", "botao-buscar", "medicamento", "lote"].forEach((id) => {
        $(id).disabled = false;
        $(id).readOnly = false;
    });
    $("editando-lote").hidden = true;
    estado.editandoLote = false;
}

function irParaOFormulario() {
    $("form-lote").scrollIntoView({ behavior: "smooth", block: "start" });
}

function novoLoteDe(remedio) {
    limparFormularioDoLote();
    $("medicamento").replaceChildren(new Option(remedio.apresentacao ? `${remedio.nome} (${remedio.apresentacao})` : remedio.nome, String(remedio.id)));
    $("medicamento").value = String(remedio.id);
    if (remedio.minimo > 0) $("estoque-minimo").value = String(remedio.minimo);
    irParaOFormulario();
    $("lote").focus({ preventScroll: true });
}

function editarLote(remedio, lote) {
    limparFormularioDoLote();
    $("medicamento").replaceChildren(new Option(remedio.apresentacao ? `${remedio.nome} (${remedio.apresentacao})` : remedio.nome, String(remedio.id)));
    $("medicamento").value = String(remedio.id);
    $("lote").value = lote.lote;
    $("validade").value = lote.validade;
    $("quantidade").value = String(lote.quantidade);
    $("preco-venda").value = String(lote.precoVenda).replace(".", ",");
    $("preco-custo").value = lote.precoCusto === null ? "" : String(lote.precoCusto).replace(".", ",");
    $("fornecedor").value = lote.fornecedor || "";
    if (remedio.minimo > 0) $("estoque-minimo").value = String(remedio.minimo);
    // o código do lote e o remédio identificam o lote: para trocar, remova e cadastre de novo
    ["busca-remedio", "botao-buscar", "medicamento"].forEach((id) => { $(id).disabled = true; });
    $("lote").readOnly = true;
    $("texto-editando-lote").textContent = `Alterando o lote ${lote.lote} de ${remedio.nome}.`;
    $("editando-lote").hidden = false;
    estado.editandoLote = true;
    irParaOFormulario();
}

async function removerLote(remedio, lote) {
    if (!window.confirm(`Remover o lote ${lote.lote} de ${remedio.nome}?`)) return;
    if (await enviar("/api/painel/estoque", { acao: "remover", lote_id: String(lote.id) })) {
        await carregarEstoque();
        mostrarOk("Lote removido.");
    }
}

async function definirMinimo(remedio) {
    const resposta = window.prompt(`Estoque mínimo de ${remedio.nome} (unidades):`, String(remedio.minimo));
    if (resposta === null) return;
    if (await enviar("/api/painel/estoque", { acao: "minimo", medicamento: String(remedio.id), estoque_minimo: resposta.trim() })) {
        await carregarEstoque();
        mostrarOk("Estoque mínimo salvo.");
    }
}

async function buscarRemedio() {
    const texto = $("busca-remedio").value.trim();
    if (texto.length < 3) {
        mostrarAviso("Digite pelo menos 3 letras do remédio.");
        return;
    }
    $("aviso").hidden = true;
    const resposta = await fetch(`/api/medicamentos?nome=${encodeURIComponent(texto)}`);
    if (!resposta.ok) {
        mostrarAviso("Não consegui buscar agora. Tente de novo.");
        return;
    }
    const remedios = await resposta.json();
    const select = $("medicamento");
    if (remedios.length === 0) {
        select.replaceChildren(new Option("Nenhum remédio com esse nome", ""));
    } else {
        select.replaceChildren(...remedios.map((r) => new Option(r.nome, String(r.id))));
    }
}

async function salvarLote(evento) {
    evento.preventDefault();
    const campos = {
        acao: "salvar",
        lote: $("lote").value.trim(),
        validade: $("validade").value,
        quantidade: $("quantidade").value.trim(),
        preco_venda: $("preco-venda").value.trim(),
        preco_custo: $("preco-custo").value.trim(),
        fornecedor: $("fornecedor").value.trim(),
        estoque_minimo: $("estoque-minimo").value.trim(),
    };
    if (!$("bloco-novo").hidden) {
        campos.novo_nome = $("novo-nome").value.trim();
        campos.novo_apresentacao = $("novo-apresentacao").value.trim();
        campos.novo_principio_ativo = $("novo-principio").value.trim();
        campos.novo_tipo = $("novo-tipo").value;
        campos.novo_exige_receita = $("novo-receita").checked ? "sim" : "nao";
    } else {
        if (!$("medicamento").value) {
            mostrarAviso("Escolha o remédio na busca ou cadastre um novo.");
            return;
        }
        campos.medicamento = $("medicamento").value;
    }
    if (await enviar("/api/painel/estoque", campos)) {
        const edicao = estado.editandoLote;
        limparFormularioDoLote();
        await carregarEstoque();
        mostrarOk(edicao ? "Lote alterado." : "Lote salvo.");
    }
}

// ----- serviços gratuitos -----

async function carregarServicos() {
    const resposta = await fetch("/api/painel/servicos");
    if (!resposta.ok) {
        mostrarAviso("Não consegui carregar os serviços. Recarregue a página.");
        return;
    }
    const dados = await resposta.json();
    estado.servicos = dados.servicos;
    const select = $("servico");
    const escolhido = select.value;
    select.replaceChildren(
        ...dados.catalogo.map((s) => new Option(s.nome, String(s.id))),
        new Option("Outro (digitar o nome)", ""),
    );
    if ([...select.options].some((o) => o.value === escolhido)) select.value = escolhido;
    alternarNomeProprio();
    desenharServicos();
}

function alternarNomeProprio() {
    const proprio = $("servico").value === "";
    $("bloco-nome-proprio").hidden = !proprio;
    $("nome-proprio").required = proprio;
}

function desenharServicos() {
    const lista = $("lista-servicos");
    lista.replaceChildren(...estado.servicos.map(criarLinhaDoServico));
    $("vazio-servicos").hidden = estado.servicos.length > 0;
}

function criarLinhaDoServico(servico) {
    const li = el("li", "list-group-item d-flex flex-wrap justify-content-between align-items-center gap-2");
    const texto = el("div");
    const nome = el("span", "d-block fw-bold", servico.nome);
    const marcas = el("span", "d-flex flex-wrap gap-1 my-1");
    marcas.append(
        servico.ativo ? badge("text-bg-success", "Disponível") : badge("text-bg-secondary", "Pausado"),
        servico.exigeAgendamento ? badge("text-bg-info", "Precisa agendar") : badge("text-bg-light border", "Sem agendamento"),
    );
    texto.append(nome, marcas);
    if (servico.diasHorarios) texto.append(el("small", "d-block text-body-secondary", servico.diasHorarios));
    if (servico.observacao) texto.append(el("small", "d-block text-body-secondary", servico.observacao));
    const acoes = el("div", "d-flex flex-wrap gap-2");
    acoes.append(
        botao("btn-outline-primary", "Alterar", () => editarServico(servico)),
        botao("btn-outline-danger", "Remover", () => removerServico(servico)),
    );
    li.append(texto, acoes);
    return li;
}

function limparFormularioDoServico() {
    $("form-servico").reset();
    $("servico-ativo").checked = true;
    estado.editandoServico = null;
    $("editando-servico").hidden = true;
    alternarNomeProprio();
}

function editarServico(servico) {
    limparFormularioDoServico();
    estado.editandoServico = servico.id;
    $("servico").value = servico.servicoId === null ? "" : String(servico.servicoId);
    alternarNomeProprio();
    if (servico.servicoId === null) $("nome-proprio").value = servico.nome;
    $("dias-horarios").value = servico.diasHorarios || "";
    $("observacao").value = servico.observacao || "";
    $("exige-agendamento").checked = servico.exigeAgendamento;
    $("servico-ativo").checked = servico.ativo;
    $("texto-editando-servico").textContent = `Alterando: ${servico.nome}.`;
    $("editando-servico").hidden = false;
    $("form-servico").scrollIntoView({ behavior: "smooth", block: "start" });
}

async function removerServico(servico) {
    if (!window.confirm(`Remover o serviço "${servico.nome}"?`)) return;
    if (await enviar("/api/painel/servicos", { acao: "remover", id: String(servico.id) })) {
        if (estado.editandoServico === servico.id) limparFormularioDoServico();
        await carregarServicos();
        mostrarOk("Serviço removido.");
    }
}

async function salvarServico(evento) {
    evento.preventDefault();
    const campos = {
        acao: "salvar",
        servico: $("servico").value,
        nome_personalizado: $("servico").value === "" ? $("nome-proprio").value.trim() : "",
        dias_horarios: $("dias-horarios").value.trim(),
        observacao: $("observacao").value.trim(),
        exige_agendamento: $("exige-agendamento").checked ? "sim" : "nao",
        ativo: $("servico-ativo").checked ? "sim" : "nao",
    };
    if (estado.editandoServico !== null) campos.id = String(estado.editandoServico);
    if (await enviar("/api/painel/servicos", campos)) {
        const edicao = estado.editandoServico !== null;
        limparFormularioDoServico();
        await carregarServicos();
        mostrarOk(edicao ? "Serviço alterado." : "Serviço salvo.");
    }
}

// ----- início -----

async function iniciar() {
    const resposta = await fetch("/api/painel");
    if (resposta.status === 401) {
        $("sem-entrar").hidden = false;
        return;
    }
    if (resposta.status === 403) {
        $("sem-acesso").hidden = false;
        return;
    }
    if (!resposta.ok) {
        mostrarAviso("Não consegui carregar o painel. Recarregue a página.");
        return;
    }
    const dados = await resposta.json();
    $("nome-farmacia").textContent = dados.farmacia.nome;
    const pendente = dados.farmacia.situacao === "pendente";
    $("aviso-pendente").hidden = !pendente;
    // o aviso do mapa só faz sentido para farmácia já aprovada: a pendente fica fora do mapa de propósito
    $("aviso-posicao").hidden = dados.farmacia.temPosicao || pendente;
    $("conteudo").hidden = false;
    await Promise.all([carregarEstoque(), carregarServicos()]);
}

$("botao-buscar").addEventListener("click", buscarRemedio);
$("busca-remedio").addEventListener("keydown", (evento) => {
    // Enter na busca procura o remédio, não envia o lote
    if (evento.key === "Enter") {
        evento.preventDefault();
        buscarRemedio();
    }
});
$("alternar-novo").addEventListener("click", () => mostrarBlocoNovo(true));
$("voltar-busca").addEventListener("click", () => mostrarBlocoNovo(false));
$("cancelar-lote").addEventListener("click", limparFormularioDoLote);
$("form-lote").addEventListener("submit", salvarLote);
$("filtro-estoque").addEventListener("input", desenharEstoque);
$("servico").addEventListener("change", alternarNomeProprio);
$("cancelar-servico").addEventListener("click", limparFormularioDoServico);
$("form-servico").addEventListener("submit", salvarServico);

iniciar();

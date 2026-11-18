"use strict";
// Doações recebidas pela farmácia: conferir, recusar, atender pedidos.
// O id da farmácia vem do servidor (pela sessão), nunca desta página.

const $ = (id) => document.getElementById(id);

function el(tag, classes, texto) {
    const elemento = document.createElement(tag);
    if (classes) elemento.className = classes;
    if (texto !== undefined && texto !== null) elemento.textContent = texto;
    return elemento;
}

function aviso(texto, tipo = "danger") {
    const caixa = tipo === "success" ? $("aviso-ok") : $("aviso");
    const outra = tipo === "success" ? $("aviso") : $("aviso-ok");
    outra.hidden = true;
    caixa.textContent = texto;
    caixa.hidden = false;
    if (tipo === "success") setTimeout(() => { caixa.hidden = true; }, 4000);
}

function data(iso) {
    const [ano, mes, dia] = iso.slice(0, 10).split("-");
    return `${dia}/${mes}/${ano}`;
}

async function enviar(campos) {
    $("aviso").hidden = true;
    let resposta;
    try {
        resposta = await fetch("/api/painel/doacoes", { method: "POST", body: new URLSearchParams(campos) });
    } catch (erro) {
        aviso("Sem conexão com o servidor. Tente de novo.");
        return null;
    }
    const dados = await resposta.json().catch(() => ({}));
    if (!resposta.ok) {
        aviso(dados.erro || "Não consegui salvar agora. Tente de novo.");
        return null;
    }
    return dados;
}

function desenharResumo(resumo) {
    const corpo = $("corpo-resumo");
    corpo.replaceChildren();
    $("tabela-resumo").hidden = resumo.length === 0;
    $("vazio-resumo").hidden = resumo.length > 0;
    for (const r of resumo) {
        const linha = el("tr");
        linha.append(
            el("td", "fw-semibold", r.medicamento),
            el("td", "text-end", String(r.doacoesDisponiveis)),
            el("td", "text-end", String(r.pedidosAbertos)),
            el("td", "text-end fw-bold", String(r.podeAtender)),
        );
        corpo.append(linha);
    }
}

function desenharPendentes(pendentes) {
    const lista = $("lista-pendentes");
    lista.replaceChildren();
    $("vazio-pendentes").hidden = pendentes.length > 0;
    for (const d of pendentes) {
        const item = el("li", "list-group-item d-flex justify-content-between align-items-center gap-2 flex-wrap");
        const texto = el("div");
        texto.append(
            el("div", "fw-semibold", `${d.medicamento} · ${d.quantidade} un.`),
            el("div", "small text-body-secondary", `validade ${data(d.validade)}`),
        );
        const botoes = el("div", "d-flex gap-2");
        const confirmar = el("button", "btn btn-sm btn-primary rounded-pill", "Confirmar recebimento");
        confirmar.type = "button";
        confirmar.addEventListener("click", () => decidir("confirmar", d.id));
        const recusar = el("button", "btn btn-sm btn-outline-danger rounded-pill", "Recusar");
        recusar.type = "button";
        recusar.addEventListener("click", () => decidir("recusar", d.id));
        botoes.append(confirmar, recusar);
        item.append(texto, botoes);
        lista.append(item);
    }
}

function desenharPedidos(pedidos) {
    const lista = $("lista-pedidos");
    lista.replaceChildren();
    $("vazio-pedidos").hidden = pedidos.length > 0;
    for (const p of pedidos) {
        const item = el("li", "list-group-item d-flex justify-content-between align-items-center gap-2 flex-wrap");
        const texto = el("div");
        texto.append(
            el("div", "fw-semibold", `${p.medicamento} · ${p.pessoa}`),
            el("div", "small text-body-secondary", `faltam ${p.quantidadeRestante} un. · pode dar agora ${p.podeAtender} un.`),
        );
        const acao = el("div", "d-flex gap-2 align-items-center");
        const quantidade = el("input", "form-control form-control-sm");
        quantidade.type = "number";
        quantidade.min = "1";
        quantidade.max = String(p.quantidadeRestante);
        quantidade.placeholder = "Quantas";
        quantidade.style.width = "7rem";
        const entregar = el("button", "btn btn-sm btn-primary rounded-pill", "Entregar");
        entregar.type = "button";
        entregar.disabled = p.podeAtender === 0;
        entregar.addEventListener("click", () => atender(p.necessidadeId, quantidade.value));
        acao.append(quantidade, entregar);
        item.append(texto, acao);
        lista.append(item);
    }
}

async function decidir(acao, doacao) {
    if (acao === "recusar" && !confirm("Recusar esta doação? Ela não aparece mais para repasse.")) return;
    const dados = await enviar({ acao, doacao });
    if (dados) {
        aviso(acao === "confirmar" ? "Doação liberada para repasse." : "Doação recusada.", "success");
        await carregar();
    }
}

async function atender(necessidade, quantidade) {
    const dados = await enviar({ acao: "atender", necessidade, quantidade });
    if (dados) {
        aviso(`Entrega registrada: ${dados.entregue} un. para o pedido.`, "success");
        await carregar();
    }
}

async function carregar() {
    let resposta;
    try {
        resposta = await fetch("/api/painel/doacoes");
    } catch (erro) {
        aviso("Sem conexão com o servidor. Recarregue a página.");
        return;
    }
    if (resposta.status === 401) {
        $("sem-entrar").hidden = false;
        return;
    }
    if (!resposta.ok) {
        const dados = await resposta.json().catch(() => ({}));
        aviso(dados.erro || "Não consegui carregar as doações. Recarregue a página.");
        $("conteudo").hidden = true;
        return;
    }
    const dados = await resposta.json();
    $("conteudo").hidden = false;
    desenharResumo(dados.resumo);
    desenharPendentes(dados.pendentes);
    desenharPedidos(dados.pedidos);
}

carregar();

"use strict";
// Doar e pedir remédio. Todo texto vindo do servidor entra na página por textContent.

const $ = (id) => document.getElementById(id);

const STATUS_DOACAO = {
    pendente: "Aguardando a farmácia conferir",
    disponivel: "Disponível para repasse",
    esgotada: "Repassada por completo",
    recusada: "Recusada pela farmácia",
};
const STATUS_PEDIDO = { aberta: "Aberto", atendida: "Atendido", cancelada: "Cancelado" };

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

async function enviar(url, campos) {
    $("aviso").hidden = true;
    let resposta;
    try {
        resposta = await fetch(url, { method: "POST", body: new URLSearchParams(campos) });
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

// Busca de remédio: o usuário digita, a lista do select vem do servidor.
function ligarBusca(campo, select) {
    let espera;
    campo.addEventListener("input", () => {
        clearTimeout(espera);
        espera = setTimeout(() => buscarRemedio(campo.value, select), 300);
    });
}

async function buscarRemedio(texto, select) {
    const termo = texto.trim();
    if (termo.length < 3) {
        select.replaceChildren(new Option("Digite pelo menos 3 letras", ""));
        return;
    }
    const resposta = await fetch(`/api/medicamentos?nome=${encodeURIComponent(termo)}`);
    if (!resposta.ok) {
        aviso("Não consegui buscar o remédio agora. Tente de novo.");
        return;
    }
    const remedios = await resposta.json();
    if (remedios.length === 0) {
        select.replaceChildren(new Option("Nenhum remédio com esse nome", ""));
    } else {
        select.replaceChildren(...remedios.map((r) => new Option(r.nome, String(r.id))));
    }
}

async function carregarFarmacias() {
    const resposta = await fetch("/api/doacoes/farmacias");
    if (!resposta.ok) {
        aviso("Não consegui carregar a lista de farmácias. Recarregue a página.");
        return;
    }
    const farmacias = await resposta.json();
    $("doar-farmacia").replaceChildren(
        new Option("Escolha uma farmácia", ""),
        ...farmacias.map((f) => new Option(`${f.nome} · ${f.endereco}`, String(f.id))),
    );
}

async function carregarMinhas() {
    const [doacoes, pedidos] = await Promise.all([
        fetch("/api/doacoes/minhas").then((r) => (r.ok ? r.json() : [])),
        fetch("/api/necessidades/minhas").then((r) => (r.ok ? r.json() : [])),
    ]);
    desenharDoacoes(doacoes);
    desenharPedidos(pedidos);
}

function desenharDoacoes(doacoes) {
    const lista = $("lista-doacoes");
    lista.replaceChildren();
    $("vazio-doacoes").hidden = doacoes.length > 0;
    for (const d of doacoes) {
        const item = el("li", "list-group-item");
        item.append(
            el("div", "fw-semibold", `${d.medicamento} · ${d.quantidadeInicial} un.`),
            el("div", "small text-body-secondary",
                `${d.farmacia} · validade ${data(d.validade)} · ${STATUS_DOACAO[d.status] || d.status}`),
        );
        if (d.status === "disponivel" || d.status === "esgotada") {
            item.append(el("div", "small", `Restam ${d.quantidadeRestante} un. para repasse`));
        }
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
            el("div", "fw-semibold", `${p.medicamento} · ${p.quantidade} un.`),
            el("div", "small text-body-secondary",
                `${STATUS_PEDIDO[p.status] || p.status}` +
                (p.status === "aberta" ? ` · faltam ${p.quantidadeRestante} un.` : "")),
        );
        item.append(texto);
        if (p.status === "aberta") {
            const cancelar = el("button", "btn btn-sm btn-outline-secondary rounded-pill", "Cancelar pedido");
            cancelar.type = "button";
            cancelar.addEventListener("click", () => cancelarPedido(p.id));
            item.append(cancelar);
        }
        lista.append(item);
    }
}

async function cancelarPedido(id) {
    if (!confirm("Cancelar este pedido?")) return;
    const dados = await enviar("/api/necessidades/cancelar", { id });
    if (dados) {
        aviso("Pedido cancelado.", "success");
        await carregarMinhas();
    }
}

async function iniciar() {
    const usuario = await Sessao.usuario();
    if (!usuario) {
        $("sem-entrar").hidden = false;
        return;
    }
    if (usuario.tipo === "admin") {
        $("so-cidadao").hidden = false;
        return;
    }
    $("conteudo").hidden = false;

    // a validade mínima é 30 dias à frente, a mesma regra do servidor
    const minima = new Date();
    minima.setDate(minima.getDate() + 30);
    $("doar-validade").min = minima.toISOString().slice(0, 10);

    ligarBusca($("doar-busca"), $("doar-medicamento"));
    ligarBusca($("pedir-busca"), $("pedir-medicamento"));

    $("form-doar").addEventListener("submit", async (evento) => {
        evento.preventDefault();
        const dados = await enviar("/api/doacoes", {
            medicamento: $("doar-medicamento").value,
            farmacia: $("doar-farmacia").value,
            quantidade: $("doar-quantidade").value,
            validade: $("doar-validade").value,
        });
        if (dados) {
            evento.target.reset();
            aviso("Doação registrada. A farmácia vai conferir e liberar para repasse.", "success");
            await carregarMinhas();
        }
    });

    $("form-pedir").addEventListener("submit", async (evento) => {
        evento.preventDefault();
        const dados = await enviar("/api/necessidades", {
            medicamento: $("pedir-medicamento").value,
            quantidade: $("pedir-quantidade").value,
        });
        if (dados) {
            evento.target.reset();
            aviso("Pedido registrado. Quando uma farmácia tiver esse remédio doado, ela entrega para você.", "success");
            await carregarMinhas();
        }
    });

    await carregarFarmacias();
    await carregarMinhas();
}

iniciar();

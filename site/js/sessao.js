// esconder item não protege nada: quem protege é o servidor
class Sessao {
    static async usuario() {
        try {
            const resposta = await fetch("/api/eu");
            return resposta.ok ? await resposta.json() : null;
        } catch (erro) {
            return null;
        }
    }

    static async acessoFarmacia() {
        try {
            const resposta = await fetch("/api/acesso-farmacia");
            return resposta.ok ? await resposta.json() : { status: "nenhum", farmacia: null };
        } catch (erro) {
            return { status: "nenhum", farmacia: null };
        }
    }

    static primeiroNome(nomeCompleto) {
        return nomeCompleto.trim().split(/\s+/)[0];
    }

    static caminhoAtual() {
        return encodeURIComponent(window.location.pathname + window.location.search);
    }

    // TAB e quebra de linha somem da URL: "/\t/evil.com" vira "//evil.com"
    static voltarSeguro(valor) {
        if (typeof valor === "string" && /^\/(?![\/\\])\S*$/.test(valor)) {
            return valor;
        }
        return "/index.html";
    }

    static mostrarNoMenu(usuario, acesso) {
        const visitante = document.getElementById("link-entrar");
        const menuConta = document.getElementById("menu-conta");
        if (visitante) visitante.hidden = Boolean(usuario);
        if (menuConta) menuConta.hidden = !usuario;
        if (!usuario) return;

        // nome sempre com textContent, nunca HTML
        document.getElementById("nome-usuario").textContent = `Olá, ${Sessao.primeiroNome(usuario.nome)}`;
        document.getElementById("item-admin").hidden = usuario.tipo !== "admin";
        // o servidor diz se o painel já está liberado (farmácia aprovada, ou cadastro novo ainda em análise)
        document.getElementById("item-painel").hidden = !(acesso && acesso.painel === true);
    }

    static async iniciar() {
        const usuario = await Sessao.usuario();
        const acesso = usuario && usuario.tipo !== "admin" ? await Sessao.acessoFarmacia() : null;
        Sessao.mostrarNoMenu(usuario, acesso);
        const sair = document.getElementById("botao-sair-menu");
        if (sair) {
            sair.addEventListener("click", Sessao.sair);
        }
        return usuario;
    }

    static async sair() {
        await fetch("/api/sair", { method: "POST" });
        window.location.href = "index.html";
    }

    static marcarEntrada() {
        try {
            sessionStorage.setItem("farmaciadigital.entrou", "1");
        } catch (erro) {
        }
    }

    static async avisarEntrada() {
        let marcado = false;
        try {
            marcado = sessionStorage.getItem("farmaciadigital.entrou") === "1";
            sessionStorage.removeItem("farmaciadigital.entrou");
        } catch (erro) {
            return;
        }
        const toast = document.getElementById("toast-entrou");
        if (!marcado || !toast) return;
        const usuario = await Sessao.usuario();
        if (!usuario) return;
        document.getElementById("texto-toast").textContent = `Você entrou como ${Sessao.primeiroNome(usuario.nome)}`;
        bootstrap.Toast.getOrCreateInstance(toast).show();
    }
}

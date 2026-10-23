
const DIAS_ORDEM = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];
const CODIGOS_DIA = [...DIAS_ORDEM, "PH"];

const NOMES_CURTOS = { Mo: "Seg", Tu: "Ter", We: "Qua", Th: "Qui", Fr: "Sex", Sa: "Sáb", Su: "Dom", PH: "Feriados" };

// 20/11: Consciência Negra (feriado nacional pela Lei 14.759/2023)
const FERIADOS_FIXOS = ["1-1", "4-21", "5-1", "9-7", "10-12", "11-2", "11-15", "11-20", "12-25"];

// Sorocaba: UTC-3, sem horário de verão desde 2019
const DESLOCAMENTO_SOROCABA_H = 3;

class Horario {
    static DIAS_ORDEM = DIAS_ORDEM;

    static somenteDigitos(texto, min, max) {
        if (texto.length < min || texto.length > max) return false;
        return [...texto].every((c) => c >= "0" && c <= "9");
    }

    static paraMinutos(texto) {
        let hora;
        let minuto;
        if (texto.includes(":")) {
            [hora, minuto] = texto.split(":");
            if (!Horario.somenteDigitos(hora, 1, 2) || !Horario.somenteDigitos(minuto, 2, 2)) return null;
        } else {
            if (!Horario.somenteDigitos(texto, 3, 4)) return null;
            hora = texto.slice(0, -2);
            minuto = texto.slice(-2);
        }
        const h = Number(hora);
        const min = Number(minuto);
        if (h > 24 || min > 59 || (h === 24 && min !== 0)) return null;
        return h * 60 + min;
    }

    static lerDias(parteDias) {
        if (parteDias === "") return [...DIAS_ORDEM];
        const marcados = new Set();
        for (const item of parteDias.split(" ").join("").split(",")) {
            if (item === "PH") {
                marcados.add("PH");
                continue;
            }
            const pedacos = item.split("-");
            if (pedacos.length > 2) return null;
            const inicio = DIAS_ORDEM.indexOf(pedacos[0]);
            const fim = pedacos.length === 2 ? DIAS_ORDEM.indexOf(pedacos[1]) : inicio;
            if (inicio < 0 || fim < 0) return null;
            for (let i = inicio; ; i = (i + 1) % 7) {
                marcados.add(DIAS_ORDEM[i]);
                if (i === fim) break;
            }
        }
        return CODIGOS_DIA.filter((c) => marcados.has(c));
    }

    static lerFaixas(parteHoras) {
        const faixas = [];
        for (const trecho of parteHoras.split(",")) {
            const pedacos = trecho.split("-").map((p) => p.trim());
            if (pedacos.length !== 2) return null;
            const ini = Horario.paraMinutos(pedacos[0]);
            const fim = Horario.paraMinutos(pedacos[1]);
            if (ini === null || fim === null || ini === fim) return null;
            faixas.push([ini, fim]);
        }
        return faixas;
    }

    static lerRegra(regra) {
        const palavras = regra.split(" ").filter((p) => p !== "");
        const ultima = (palavras[palavras.length - 1] || "").toLowerCase();
        if (ultima === "off" || ultima === "closed") {
            return { parteDias: palavras.slice(0, -1).join(" "), valor: { tipo: "fechado" } };
        }
        const inicioHoras = [...regra].findIndex((c) => c >= "0" && c <= "9");
        if (inicioHoras < 0) return null;
        const faixas = Horario.lerFaixas(regra.slice(inicioHoras).trim());
        if (!faixas) return null;
        return { parteDias: regra.slice(0, inicioHoras).trim(), valor: { tipo: "faixas", faixas } };
    }

    static interpretar(texto) {
        const original = String(texto ?? "").trim();
        if (!original) return { ok: false };

        const dias = {};
        CODIGOS_DIA.forEach((c) => { dias[c] = null; });

        if (original === "24/7") {
            CODIGOS_DIA.forEach((c) => { dias[c] = { tipo: "24h" }; });
            return { ok: true, dias };
        }

        const regras = original.split(";").map((r) => r.trim()).filter((r) => r !== "");
        if (regras.length === 0) return { ok: false };

        for (const regra of regras) {
            const lida = Horario.lerRegra(regra);
            if (!lida) return { ok: false };
            const listaDias = Horario.lerDias(lida.parteDias);
            if (!listaDias) return { ok: false };
            listaDias.forEach((c) => { dias[c] = lida.valor; });
        }
        return { ok: true, dias };
    }

    static formatarDia(valor) {
        if (valor === null) return "não informado";
        if (valor.tipo === "fechado") return "fechado";
        if (valor.tipo === "24h") return "24 horas";
        const hora = (min) => `${String(Math.floor(min / 60)).padStart(2, "0")}:${String(min % 60).padStart(2, "0")}`;
        return valor.faixas.map(([ini, fim]) => `${hora(ini)}–${hora(fim)}`).join(", ");
    }

    static agoraNoBrasil(data = new Date()) {
        const local = new Date(data.getTime() - DESLOCAMENTO_SOROCABA_H * 60 * 60 * 1000);
        return {
            dia: (local.getUTCDay() + 6) % 7,
            minutos: local.getUTCHours() * 60 + local.getUTCMinutes(),
            ano: local.getUTCFullYear(),
            mes: local.getUTCMonth() + 1,
            diaDoMes: local.getUTCDate(),
        };
    }

    static domingoDePascoa(ano) {
        const a = ano % 19;
        const b = Math.floor(ano / 100);
        const c = ano % 100;
        const d = Math.floor(b / 4);
        const e = b % 4;
        const f = Math.floor((b + 8) / 25);
        const g = Math.floor((b - f + 1) / 3);
        const h = (19 * a + b - d - g + 15) % 30;
        const i = Math.floor(c / 4);
        const k = c % 4;
        const l = (((32 + 2 * e + 2 * i - h - k) % 7) + 7) % 7;
        const m = Math.floor((a + 11 * h + 22 * l) / 451);
        const mes = Math.floor((h + l - 7 * m + 114) / 31);
        const dia = ((h + l - 7 * m + 114) % 31) + 1;
        return { mes, dia };
    }

    static ehFeriadoNacional(agora) {
        if (!agora || !agora.ano) return false;
        const diaMovel = (deslocamento) => {
            const p = Horario.domingoDePascoa(agora.ano);
            const data = new Date(Date.UTC(agora.ano, p.mes - 1, p.dia + deslocamento));
            return `${data.getUTCMonth() + 1}-${data.getUTCDate()}`;
        };
        const mesDia = `${agora.mes}-${agora.diaDoMes}`;
        return FERIADOS_FIXOS.includes(mesDia) || mesDia === diaMovel(-2) || mesDia === diaMovel(60);
    }

    static estaAbertoAgora(interpretado, agora = Horario.agoraNoBrasil()) {
        if (!interpretado || !interpretado.ok) return undefined;
        if (Horario.ehFeriadoNacional(agora)) return undefined;

        const dias = interpretado.dias;
        const hoje = dias[DIAS_ORDEM[agora.dia]];
        const ontem = dias[DIAS_ORDEM[(agora.dia + 6) % 7]];
        if (hoje === null) return undefined;
        if (hoje.tipo === "24h") return true;

        if (hoje.tipo === "faixas") {
            for (const [ini, fim] of hoje.faixas) {
                if (fim > ini && agora.minutos >= ini && agora.minutos < fim) return true;
                if (fim < ini && agora.minutos >= ini) return true;
            }
        }

        if (ontem === null) {
            // sem o dia de ontem: depois das 06:00 é fechado; antes, não afirma nada
            return agora.minutos > 6 * 60 ? false : undefined;
        }
        if (ontem.tipo === "24h") return true;
        if (ontem.tipo === "faixas") {
            for (const [ini, fim] of ontem.faixas) {
                if (fim < ini && agora.minutos < fim) return true;
            }
        }
        return false;
    }

    static fechamentoDeHoje(interpretado, agora = Horario.agoraNoBrasil()) {
        if (!interpretado || !interpretado.ok) return null;
        const hoje = interpretado.dias[DIAS_ORDEM[agora.dia]];
        if (!hoje || hoje.tipo !== "faixas") return null;
        for (const [ini, fim] of hoje.faixas) {
            if (fim > ini && agora.minutos >= ini && agora.minutos < fim) return fim;
            if (fim < ini && agora.minutos >= ini) return fim;
        }
        return null;
    }

    static agruparDias(dias) {
        const grupos = [];
        DIAS_ORDEM.forEach((codigo) => {
            const texto = Horario.formatarDia(dias[codigo]);
            const ultimo = grupos[grupos.length - 1];
            if (ultimo && ultimo.texto === texto) {
                ultimo.codigos.push(codigo);
            } else {
                grupos.push({ codigos: [codigo], texto });
            }
        });
        const rotulo = (g) => (g.codigos.length === 1
            ? NOMES_CURTOS[g.codigos[0]]
            : `${NOMES_CURTOS[g.codigos[0]]}–${NOMES_CURTOS[g.codigos[g.codigos.length - 1]]}`);
        return {
            dias: grupos.map((g) => ({ rotulo: rotulo(g), texto: g.texto, codigos: g.codigos })),
            feriados: { rotulo: "Feriados", texto: Horario.formatarDia(dias.PH), codigos: ["PH"] },
        };
    }
}

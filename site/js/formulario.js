class Formulario {
    static marcarErro(campo, textoErro, msg) {
        const formulario = campo.form;
        campo.setCustomValidity(msg || "");
        textoErro.textContent = msg || "";
        // d-block: o texto aparece mesmo dentro de input-group
        textoErro.classList.toggle("d-block", Boolean(msg));
        campo.classList.toggle("is-invalid", Boolean(msg));
        if (!formulario) return;
        if (msg) {
            formulario.classList.add("was-validated");
        } else if (!formulario.querySelector(".is-invalid")) {
            formulario.classList.remove("was-validated");
        }
    }
}

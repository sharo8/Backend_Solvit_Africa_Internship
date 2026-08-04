(function () {
    const STORAGE_KEY = "sharo-lang";
    const EMAIL = "charorodrigue12@gmail.com";

    function getActiveLang() {
        return document.documentElement.getAttribute("data-lang") === "fr" ? "fr" : "en";
    }

    function getDict() {
        const lang = getActiveLang();
        const pack = window.SHARO_I18N;
        if (!pack) return {};
        return pack[lang] || pack.en || {};
    }

    function applyLanguage(lang) {
        const pack = window.SHARO_I18N;
        if (!pack) return;

        const safe = lang === "fr" ? "fr" : "en";
        const dict = pack[safe] || pack.en;

        document.documentElement.lang = safe === "fr" ? "fr" : "en";
        document.documentElement.setAttribute("data-lang", safe);
        try {
            localStorage.setItem(STORAGE_KEY, safe);
        } catch (e) {
            /* ignore quota / private mode */
        }

        const meta = document.querySelector('meta[name="description"]');
        if (meta && dict.meta_desc) {
            meta.setAttribute("content", dict.meta_desc);
        }
        if (dict.page_title) {
            document.title = dict.page_title;
        }

        document.querySelectorAll("[data-i18n]").forEach(function (el) {
            const key = el.getAttribute("data-i18n");
            if (key && dict[key] !== undefined) {
                el.textContent = dict[key];
            }
        });

        document.querySelectorAll("[data-i18n-html]").forEach(function (el) {
            const key = el.getAttribute("data-i18n-html");
            if (key && dict[key] !== undefined) {
                el.innerHTML = dict[key];
            }
        });

        document.querySelectorAll("[data-i18n-placeholder]").forEach(function (el) {
            const key = el.getAttribute("data-i18n-placeholder");
            if (key && dict[key] !== undefined) {
                el.setAttribute("placeholder", dict[key]);
            }
        });

        document.querySelectorAll("[data-i18n-aria]").forEach(function (el) {
            const key = el.getAttribute("data-i18n-aria");
            if (key && dict[key] !== undefined) {
                el.setAttribute("aria-label", dict[key]);
            }
        });

        document.querySelectorAll("[data-i18n-alt]").forEach(function (el) {
            const key = el.getAttribute("data-i18n-alt");
            if (key && dict[key] !== undefined) {
                el.setAttribute("alt", dict[key]);
            }
        });

        document.querySelectorAll("[data-mailto-cv]").forEach(function (a) {
            const subj = dict.mail_cv_subject || "Resume / CV request";
            a.setAttribute("href", "mailto:" + EMAIL + "?subject=" + encodeURIComponent(subj));
        });

        const langSwitch = document.getElementById("lang-switch");
        if (langSwitch && dict.lang_group) {
            langSwitch.setAttribute("aria-label", dict.lang_group);
        }

        document.querySelectorAll(".lang-btn").forEach(function (btn) {
            const active = btn.getAttribute("data-lang") === safe;
            btn.classList.toggle("active-lang", active);
            btn.setAttribute("aria-pressed", active ? "true" : "false");
        });
    }

    [...document.querySelectorAll(".control")].forEach(function (button) {
        button.addEventListener("click", function () {
            document.querySelector(".active-btn").classList.remove("active-btn");
            this.classList.add("active-btn");
            document.querySelector(".active").classList.remove("active");
            document.getElementById(button.dataset.id).classList.add("active");
        });
    });

    document.querySelector(".theme-btn").addEventListener("click", function () {
        document.body.classList.toggle("light-mode");
    });

    document.querySelectorAll(".lang-btn").forEach(function (btn) {
        btn.addEventListener("click", function () {
            const lang = this.getAttribute("data-lang");
            if (lang === "en" || lang === "fr") {
                applyLanguage(lang);
            }
        });
    });

    const contactForm = document.getElementById("contact-form");
    if (contactForm) {
        contactForm.addEventListener("submit", function (e) {
            e.preventDefault();
            const dict = getDict();
            const name = document.getElementById("contact-name").value.trim();
            const email = document.getElementById("contact-email").value.trim();
            const subject = document.getElementById("contact-subject").value.trim();
            const message = document.getElementById("contact-message").value.trim();
            const nl = name ? dict.mail_name + ": " + name + "\n" : "";
            const em = email ? dict.mail_email + ": " + email + "\n\n" : "";
            const body = nl + em + (message || "");
            const href =
                "mailto:" +
                EMAIL +
                "?subject=" +
                encodeURIComponent(subject || dict.mail_subject_default || "Portfolio contact") +
                "&body=" +
                encodeURIComponent(body);
            window.location.href = href;
        });
    }

    (function initLang() {
        let initial = "en";
        try {
            const saved = localStorage.getItem(STORAGE_KEY);
            if (saved === "fr" || saved === "en") {
                initial = saved;
            }
        } catch (e) {
            initial = "en";
        }
        applyLanguage(initial);
    })();
})();

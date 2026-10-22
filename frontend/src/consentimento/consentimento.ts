// Aviso de cookies (RF18 RN2). O HTML do aviso vem do servidor, escondido e
// igual para todos os visitantes (pode ficar em cache); este script so o mostra
// quando ainda nao ha escolha. A escolha fica num cookie, e nao no
// localStorage, para valer mesmo com o armazenamento bloqueado e para o
// servidor poder le-la ao montar os anuncios (issue #12).

export const NOME_COOKIE = 'consentimento-anuncios';
const UM_ANO_EM_SEGUNDOS = 365 * 24 * 60 * 60;

export type Consentimento = 'personalizados' | 'nao-personalizados';

/** Disparado no document quando o visitante escolhe; os anuncios (issue #12) escutam. */
export const EVENTO_CONSENTIMENTO = 'consentimento-anuncios';

function valido(valor: string | undefined): valor is Consentimento {
  return valor === 'personalizados' || valor === 'nao-personalizados';
}

export function lerConsentimento(doc: Document = document): Consentimento | null {
  try {
    const prefixo = `${NOME_COOKIE}=`;
    const valor = doc.cookie
      .split('; ')
      .find((c) => c.startsWith(prefixo))
      ?.slice(prefixo.length);
    return valido(valor) ? valor : null;
  } catch {
    // Cookies bloqueados: trata como "ainda sem escolha"
    return null;
  }
}

export function gravarConsentimento(escolha: Consentimento, doc: Document = document): void {
  const seguro = doc.location?.protocol === 'https:' ? '; Secure' : '';
  try {
    doc.cookie = `${NOME_COOKIE}=${escolha}; Max-Age=${UM_ANO_EM_SEGUNDOS}; Path=/; SameSite=Lax${seguro}`;
  } catch {
    // Sem cookies a escolha vale so para esta pagina
  }
  doc.dispatchEvent(new CustomEvent<Consentimento>(EVENTO_CONSENTIMENTO, { detail: escolha }));
}

/**
 * Liga o aviso e o link "Preferências de cookies" do rodape.
 * Mostra o aviso so no primeiro acesso (sem escolha gravada).
 */
export function iniciarAvisoCookies(doc: Document = document): void {
  const aviso = doc.querySelector<HTMLElement>('[data-aviso-cookies]');
  if (!aviso) return;

  // O aviso fica fixo no rodape da tela: reserva o espaco para nao cobrir o fim da pagina
  const exibir = (visivel: boolean) => {
    aviso.hidden = !visivel;
    doc.body.style.paddingBottom = visivel ? `${aviso.offsetHeight}px` : '';
  };
  const mostrar = () => {
    exibir(true);
    aviso.querySelector<HTMLButtonElement>('[data-consentimento]')?.focus();
  };

  aviso.querySelectorAll<HTMLButtonElement>('[data-consentimento]').forEach((botao) => {
    botao.addEventListener('click', () => {
      const escolha = botao.dataset.consentimento;
      if (valido(escolha)) gravarConsentimento(escolha, doc);
      exibir(false);
    });
  });

  const abrir = doc.querySelector<HTMLButtonElement>('[data-abrir-aviso-cookies]');
  if (abrir) {
    abrir.hidden = false;
    abrir.addEventListener('click', mostrar);
  }

  // No primeiro acesso nao rouba o foco da pagina: so mostra
  if (lerConsentimento(doc) === null) exibir(true);
}

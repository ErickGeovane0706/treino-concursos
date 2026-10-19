import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EVENTO_CONSENTIMENTO, iniciarAvisoCookies, lerConsentimento, NOME_COOKIE } from './consentimento';

const HTML = `
  <button type="button" data-abrir-aviso-cookies hidden>Preferências de cookies</button>
  <div data-aviso-cookies hidden>
    <button type="button" data-consentimento="personalizados">Aceitar</button>
    <button type="button" data-consentimento="nao-personalizados">Recusar</button>
  </div>`;

const aviso = () => document.querySelector<HTMLElement>('[data-aviso-cookies]')!;
const botao = (texto: string) =>
  [...document.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent === texto)!;

function apagarCookie() {
  document.cookie = `${NOME_COOKIE}=; Max-Age=0; Path=/`;
}

describe('aviso de cookies (RF18 RN2)', () => {
  beforeEach(() => {
    apagarCookie();
    document.body.innerHTML = HTML;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    apagarCookie();
  });

  it('aparece no primeiro acesso', () => {
    iniciarAvisoCookies();
    expect(aviso().hidden).toBe(false);
  });

  it('não aparece quando já há escolha', () => {
    document.cookie = `${NOME_COOKIE}=nao-personalizados; Path=/`;
    iniciarAvisoCookies();
    expect(aviso().hidden).toBe(true);
  });

  it('grava a escolha, esconde o aviso e avisa os anúncios', () => {
    const ouvinte = vi.fn();
    document.addEventListener(EVENTO_CONSENTIMENTO, ouvinte);
    iniciarAvisoCookies();

    botao('Recusar').click();

    expect(lerConsentimento()).toBe('nao-personalizados');
    expect(aviso().hidden).toBe(true);
    expect((ouvinte.mock.calls[0][0] as CustomEvent).detail).toBe('nao-personalizados');
    document.removeEventListener(EVENTO_CONSENTIMENTO, ouvinte);
  });

  it('o link do rodapé reabre o aviso para mudar a escolha', () => {
    document.cookie = `${NOME_COOKIE}=personalizados; Path=/`;
    iniciarAvisoCookies();
    const abrir = botao('Preferências de cookies');
    expect(abrir.hidden).toBe(false);

    abrir.click();
    expect(aviso().hidden).toBe(false);
    botao('Recusar').click();
    expect(lerConsentimento()).toBe('nao-personalizados');
  });

  it('ignora valor desconhecido no cookie', () => {
    document.cookie = `${NOME_COOKIE}=talvez; Path=/`;
    expect(lerConsentimento()).toBeNull();
  });

  it('continua funcionando com cookies bloqueados', () => {
    vi.spyOn(document, 'cookie', 'get').mockImplementation(() => {
      throw new DOMException('bloqueado', 'SecurityError');
    });
    vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {
      throw new DOMException('bloqueado', 'SecurityError');
    });
    iniciarAvisoCookies();
    expect(aviso().hidden).toBe(false);

    botao('Aceitar').click();
    expect(aviso().hidden).toBe(true);
  });

  it('não faz nada em página sem o aviso', () => {
    document.body.innerHTML = '';
    expect(() => iniciarAvisoCookies()).not.toThrow();
  });
});

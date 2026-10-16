import { afterEach, describe, expect, it } from 'vitest';
import { act } from 'react';
import { montarIlha } from './montar';

describe('montarIlha', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('monta o conteúdo no elemento marcado pela página', async () => {
    document.body.innerHTML = '<div data-ilha="treino"></div>';
    await act(async () => montarIlha('treino', <p>olá</p>));
    expect(document.querySelector('[data-ilha="treino"]')?.textContent).toBe('olá');
  });

  it('não faz nada quando a página não tem o elemento', () => {
    expect(() => montarIlha('treino', <p>olá</p>)).not.toThrow();
  });
});

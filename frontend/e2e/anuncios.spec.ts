import { expect, test, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { ARQUIVO_DADOS, type DadosE2E } from './preparar-dados';

/**
 * RF12 RN1-RN4, RNF07 (issue #12): posicao segura dos anuncios, medida no navegador em
 * 360 px e 1920 px (um projeto do Playwright para cada largura).
 *
 * O site roda com ADS_ENABLED=true, mas toda requisicao ao Google e bloqueada aqui: nenhum
 * anuncio real carrega (e isso tambem simula um bloqueador de anuncios).
 */

const DISTANCIA_MINIMA = 100;
const DOMINIOS_ANUNCIO = /googlesyndication|doubleclick|googletagservices|adservice\.google|adtrafficquality|fundingchoicesmessages/;

const dados = (): DadosE2E => JSON.parse(readFileSync(ARQUIVO_DADOS, 'utf8')) as DadosE2E;

interface Violacao {
  bloco: string;
  elemento: string;
  distancia: number;
}

interface Medida {
  blocosVisiveis: string[];
  rotulos: string[];
  violacoes: Violacao[];
  clicaveisMedidos: number;
}

/** Menor distancia entre cada bloco de anuncio visivel e cada elemento clicavel visivel. */
async function medir(page: Page): Promise<Medida> {
  return page.evaluate(() => {
    const visivel = (e: Element) => {
      const r = e.getBoundingClientRect();
      const s = getComputedStyle(e);
      return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';
    };
    const caixa = (e: Element) => {
      const r = e.getBoundingClientRect();
      return { esq: r.left + scrollX, dir: r.right + scrollX, topo: r.top + scrollY, base: r.bottom + scrollY };
    };
    const blocos = [...document.querySelectorAll('[data-bloco-anuncio]')].filter(visivel);
    const clicaveis = [
      ...document.querySelectorAll(
        'a[href], button, input, select, textarea, summary, [role="button"], [tabindex]:not([tabindex="-1"])',
      ),
    ].filter((e) => visivel(e) && !e.closest('[data-bloco-anuncio]'));

    const violacoes: { bloco: string; elemento: string; distancia: number }[] = [];
    for (const bloco of blocos) {
      const a = caixa(bloco);
      for (const el of clicaveis) {
        const b = caixa(el);
        const dx = Math.max(0, a.esq - b.dir, b.esq - a.dir);
        const dy = Math.max(0, a.topo - b.base, b.topo - a.base);
        const distancia = Math.hypot(dx, dy);
        if (distancia < 100) {
          violacoes.push({
            bloco: (bloco as HTMLElement).dataset.blocoAnuncio ?? '?',
            elemento: `${el.tagName.toLowerCase()} "${(el.textContent ?? '').trim().slice(0, 40)}"`,
            distancia: Math.round(distancia),
          });
        }
      }
    }
    return {
      blocosVisiveis: blocos.map((b) => (b as HTMLElement).dataset.blocoAnuncio ?? '?'),
      rotulos: blocos.map((b) => b.querySelector('.rotulo-anuncio')?.textContent?.trim() ?? ''),
      violacoes,
      clicaveisMedidos: clicaveis.length,
    };
  });
}

/** Posicao (no documento) de cada elemento clicavel, para ver se algo foi empurrado. */
async function posicoesDosClicaveis(page: Page): Promise<number[]> {
  return page.evaluate(() =>
    [...document.querySelectorAll('a[href], button, summary')].map((e) =>
      Math.round(e.getBoundingClientRect().top + scrollY),
    ),
  );
}

function esperarRegrasDeAnuncio(medida: Medida, largura: number) {
  // RF12 RN1: 1 bloco no conteudo e, a partir de 1024 px, 1 lateral
  expect(medida.blocosVisiveis).toEqual(largura >= 1024 ? ['questao', 'lateral'] : ['questao']);
  // RF12 RN3: rotulo em todo bloco
  expect(medida.rotulos.every((r) => r === 'Publicidade')).toBe(true);
  expect(medida.clicaveisMedidos).toBeGreaterThan(5);
  // RF12 RN2: nenhum elemento clicavel a menos de 100 px
  expect(medida.violacoes, `elementos a menos de ${DISTANCIA_MINIMA} px de um anuncio`).toEqual([]);
}

test.beforeEach(async ({ context, baseURL }) => {
  await context.route(DOMINIOS_ANUNCIO, (rota) => rota.abort());
  // Escolha do aviso de cookies ja feita: o aviso fixo no rodape nao cobre a pagina
  await context.addCookies([{ name: 'consentimento-anuncios', value: 'nao-personalizados', url: baseURL! }]);
});

test('questão: anúncio a 100 px de todo clicável antes e depois de responder', async ({ page, viewport }) => {
  const { id, slug } = dados().questoes[0];
  await page.goto(`/questao/${id}/${slug}`);
  await expect(page.locator('[data-ilha="treino"] button.opt').first()).toBeVisible();

  esperarRegrasDeAnuncio(await medir(page), viewport!.width);

  await page.locator('[data-ilha="treino"] button.opt').first().click();
  await expect(page.getByRole('link', { name: 'Próxima questão', exact: true })).toBeFocused();
  await expect(page.getByRole('button', { name: 'Refazer' })).toBeVisible();

  esperarRegrasDeAnuncio(await medir(page), viewport!.width);
});

test('simulado: anúncio a 100 px de todo clicável com a resposta marcada', async ({ page, viewport }) => {
  await page.goto(`/simulado/${dados().simulado}/2`);
  await page.locator('[data-ilha="simulado-questao"] button.opt').nth(1).click();
  await expect(page.getByRole('button', { name: 'Deixar em branco' })).toBeVisible();

  esperarRegrasDeAnuncio(await medir(page), viewport!.width);
});

test('anúncio maior que o espaço não empurra nenhum botão (CLS)', async ({ page }) => {
  const { id, slug } = dados().questoes[1];
  await page.goto(`/questao/${id}/${slug}`);
  await page.locator('[data-ilha="treino"] button.opt').first().click();
  await expect(page.getByRole('button', { name: 'Refazer' })).toBeVisible();
  const antes = await posicoesDosClicaveis(page);

  // Simula o AdSense preenchendo cada bloco com um anuncio mais alto que o espaco reservado
  const cls = await page.evaluate(async () => {
    let total = 0;
    new PerformanceObserver((lista) => {
      for (const e of lista.getEntries() as (PerformanceEntry & { value: number; hadRecentInput: boolean })[]) {
        if (!e.hadRecentInput) total += e.value;
      }
    }).observe({ type: 'layout-shift' });
    document.querySelectorAll('ins.adsbygoogle').forEach((ins) => {
      const anuncio = document.createElement('div');
      anuncio.style.cssText = 'width:100%;height:600px;background:#ccc';
      ins.appendChild(anuncio);
    });
    await new Promise((r) => setTimeout(r, 500));
    return total;
  });

  expect(await posicoesDosClicaveis(page)).toEqual(antes);
  expect(cls).toBeLessThanOrEqual(0.1);
});

test('com bloqueador de anúncios o layout não quebra', async ({ page }) => {
  // Bloqueadores escondem os espacos de anuncio por CSS, alem de bloquear o script
  await page.addInitScript(() => {
    document.addEventListener('DOMContentLoaded', () => {
      const estilo = document.createElement('style');
      estilo.textContent = 'ins.adsbygoogle, [data-bloco-anuncio] { display: none !important; }';
      document.head.appendChild(estilo);
    });
  });
  const [primeira, segunda] = dados().questoes;
  await page.goto(`/questao/${primeira.id}/${primeira.slug}`);

  const larguras = await page.evaluate(() => ({
    conteudo: document.documentElement.scrollWidth,
    tela: document.documentElement.clientWidth,
  }));
  expect(larguras.conteudo).toBeLessThanOrEqual(larguras.tela);

  await page.locator('[data-ilha="treino"] button.opt').nth(2).click();
  await expect(page.getByRole('status')).toContainText('Você acertou');
  await page.getByRole('link', { name: 'Próxima questão', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/questao/${segunda.id}/`));
});

test('sem anúncio em página de erro e no painel (RF12 RN4)', async ({ page }) => {
  const resposta = await page.goto('/questao/999999999/nao-existe');
  expect(resposta?.status()).toBe(404);
  await expect(page.locator('[data-bloco-anuncio], ins.adsbygoogle')).toHaveCount(0);
  await expect(page.getByText('Publicidade')).toHaveCount(0);

  await page.goto('/admin/');
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  await expect(page.locator('[data-bloco-anuncio], ins.adsbygoogle')).toHaveCount(0);
});

test('a medição acusa um clicável a menos de 100 px (garante que o teste não passa à toa)', async ({ page }) => {
  const { id, slug } = dados().questoes[0];
  await page.goto(`/questao/${id}/${slug}`);
  await page.evaluate(() => {
    const anuncio = document.querySelector('[data-bloco-anuncio="questao"]')!.getBoundingClientRect();
    const botao = document.createElement('button');
    botao.textContent = 'Colado no anúncio';
    botao.style.cssText = `position:absolute;left:${anuncio.left + scrollX}px;width:120px;height:30px;top:${anuncio.bottom + scrollY + 40}px`;
    document.body.appendChild(botao);
  });

  const { violacoes } = await medir(page);

  expect(violacoes).toEqual([{ bloco: 'questao', elemento: 'button "Colado no anúncio"', distancia: 40 }]);
});

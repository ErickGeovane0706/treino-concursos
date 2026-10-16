---
name: conselho
description: Convoca cinco conselheiros (Contrarian, Primeiros Princípios, Expansionista, Outsider, Executor) para analisar uma decisão de ângulos diferentes antes de decidir. Use quando o usuário pedir "chama o conselho" / "convoca o conselho", ou quando quiser avaliar uma decisão importante de produto, arquitetura ou escopo antes de escrever código.
---

# Conselho

Cinco conselheiros analisam a mesma decisão de ângulos diferentes, **em paralelo**, e você
sintetiza. O valor está no desacordo entre eles — não achate isso numa média morna.

## 1. Defina a decisão

Antes de convocar, escreva **a decisão em uma frase**, do jeito que um conselheiro que nunca viu
este projeto entenderia. Se o pedido do usuário for vago demais para isso ("melhorar o app"),
**pergunte antes de convocar** — cinco agentes analisando uma pergunta vaga produzem cinco
respostas vagas, e custam tempo e dinheiro.

Reúna também o contexto mínimo: que arquivos importam, que restrições já são conhecidas, o que
já foi tentado.

## 2. Convoque os cinco, em paralelo

Numa **única mensagem**, faça cinco chamadas da ferramenta Agent (elas rodam em paralelo):

| subagent_type | Papel |
|---|---|
| `conselho-contrarian` | Como isso fracassa: riscos, custo escondido, premissa frágil |
| `conselho-primeiros-principios` | Desmonta as premissas e reconstrói a solução do zero |
| `conselho-expansionista` | O teto: que oportunidade isso abre ou desperdiça |
| `conselho-outsider` | A confusão que quem está dentro já não enxerga |
| `conselho-executor` | Viabilidade real, arquivos tocados e o primeiro passo |

Passe a **mesma** decisão e o **mesmo** contexto para todos — a graça é ver o que cada um faz
com o mesmo material. Inclua no prompt de cada um: a decisão em uma frase, o contexto, os
arquivos relevantes e o que já se sabe que **não** funciona.

Use `run_in_background: false` — você precisa das cinco respostas para sintetizar.

## 3. Sintetize

O relatório para o usuário, nesta ordem:

**A decisão, como eu a entendi.** Uma frase. Se os conselheiros divergiram sobre *qual era a
pergunta*, isso já é o achado principal — diga.

**Onde os cinco concordam.** Convergência entre perfis que buscam coisas opostas é o sinal mais
forte que este processo produz. Trate como quase-certeza.

**Onde discordam — e o que está em jogo.** Não resolva o desacordo escondendo-o. Diga qual fato
decidiria a briga: "o Contrarian assume que os anexos podem ser grandes; o Executor assume que
não. Ninguém mediu. Medir custa dez minutos."

**A recomendação.** Sua, não a média deles. Você leu os cinco; escolha, e diga por quê. Se um
conselheiro te convenceu, nomeie-o.

**O primeiro passo.** Concreto, feito hoje, verificável.

## Regras

- **Não convoque para o trivial.** Um bug, um ajuste de CSS, uma pergunta de fato não precisam
  de conselho — precisam de resposta. Convoque para decisões que são caras de reverter:
  arquitetura, escopo, modelo de cobrança, troca de dependência, mudança de fluxo do usuário.
- **Não deixe o conselho decidir por você.** Ele informa; quem responde ao usuário é você.
- **Preserve a discordância.** Se os cinco disserem coisas confortáveis e compatíveis, ou a
  decisão é fácil (e não precisava de conselho) ou os conselheiros foram preguiçosos — desconfie.
- **Relate o que não foi analisado.** Se um conselheiro não teve o contexto de que precisava,
  diga isso em vez de fingir que o parecer dele vale.

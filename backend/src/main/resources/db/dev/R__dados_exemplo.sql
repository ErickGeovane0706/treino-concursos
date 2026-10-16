-- Dados de exemplo para DESENVOLVIMENTO (so no perfil dev): as 8 questoes do
-- prototipo (doc/prototipo.html), todas autorais e publicadas.
-- Migration repetivel: roda de novo quando este arquivo muda, entao cada
-- INSERT ignora o que ja existe.

DO $seed$
DECLARE
    v_autor   INT;
    v_questao INT;
    q         RECORD;
    i         INT;
BEGIN
    -- Autor das questoes de exemplo: inativo e com hash invalido (nunca entra
    -- no painel). Nao conta para o admin inicial, que olha so para ADMIN.
    INSERT INTO usuarios (nome, email, senha_hash, perfil, ativo)
    VALUES ('Dados de exemplo', 'exemplo@treinoconcursos.invalid', '!', 'EDITOR', FALSE)
    ON CONFLICT DO NOTHING;
    SELECT id_usuario INTO v_autor FROM usuarios WHERE email = 'exemplo@treinoconcursos.invalid';

    INSERT INTO materias (nome, slug) VALUES
        ('Português', 'portugues'),
        ('Direito Constitucional', 'direito-constitucional'),
        ('Direito Administrativo', 'direito-administrativo'),
        ('Informática', 'informatica'),
        ('Raciocínio Lógico', 'raciocinio-logico')
    ON CONFLICT DO NOTHING;

    INSERT INTO assuntos (id_materia, nome, slug)
    SELECT m.id_materia, a.nome, a.slug
    FROM (VALUES
        ('portugues', 'Concordância verbal', 'concordancia-verbal'),
        ('portugues', 'Crase', 'crase'),
        ('direito-constitucional', 'Princípios fundamentais', 'principios-fundamentais'),
        ('direito-constitucional', 'Remédios constitucionais', 'remedios-constitucionais'),
        ('direito-administrativo', 'Princípios da administração pública', 'principios-da-administracao-publica'),
        ('informatica', 'Planilhas eletrônicas', 'planilhas-eletronicas'),
        ('raciocinio-logico', 'Lógica proposicional', 'logica-proposicional'),
        ('raciocinio-logico', 'Porcentagem', 'porcentagem')
    ) AS a(materia, nome, slug)
    JOIN materias m ON m.slug = a.materia
    ON CONFLICT DO NOTHING;

    -- Um subassunto, para exercitar a navegacao por assunto com filhos (RF07)
    INSERT INTO assuntos (id_materia, id_assunto_pai, nome, slug)
    SELECT pai.id_materia, pai.id_assunto, 'Verbo haver', 'verbo-haver'
    FROM assuntos pai JOIN materias m ON m.id_materia = pai.id_materia
    WHERE m.slug = 'portugues' AND pai.slug = 'concordancia-verbal'
    ON CONFLICT DO NOTHING;

    FOR q IN
        SELECT * FROM (VALUES
            (1, 'portugues', 'verbo-haver',
             'assinale-a-frase-correta-quanto-a-concordancia-verbal',
             'Assinale a frase correta quanto à concordância verbal.',
             'Haver no sentido de ocorrer é impessoal e fica no singular: “Houve”. As demais erram: “Faz dois anos”, “Havia candidatos”, “Deve haver” e “Existem fiscais”.',
             ARRAY['Fazem dois anos que ele se aposentou.', 'Haviam muitos candidatos na fila.',
                   'Houve muitos problemas durante a prova.', 'Devem haver soluções para o caso.',
                   'Existe muitos fiscais na sala.'], 3),
            (2, 'portugues', 'crase',
             'complete-a-frase-entregou-o-documento-funcionaria-que-chegou-primeiro',
             'Complete a frase: “Entregou o documento ___ funcionária que chegou primeiro.”',
             'Entregar exige a preposição “a”, e “funcionária” é feminino e aceita o artigo “a”. As duas se juntam e formam a crase: “à”.',
             ARRAY['a', 'à', 'á', 'as', 'às'], 2),
            (3, 'direito-constitucional', 'principios-fundamentais',
             'segundo-a-constituicao-federal-nao-e-fundamento-da-republica-federativa-do-brasil',
             'Segundo a Constituição Federal, **NÃO** é fundamento da República Federativa do Brasil:',
             'Os fundamentos (art. 1º) são soberania, cidadania, dignidade da pessoa humana, valores sociais do trabalho e da livre iniciativa e pluralismo político. A independência nacional é princípio das relações internacionais (art. 4º).',
             ARRAY['a soberania', 'a cidadania', 'o pluralismo político', 'a independência nacional',
                   'a dignidade da pessoa humana'], 4),
            (4, 'direito-constitucional', 'remedios-constitucionais',
             'o-remedio-constitucional-que-protege-direito-liquido-e-certo',
             'O remédio constitucional que protege direito líquido e certo, quando não amparado por *habeas corpus* ou *habeas data*, é o:',
             'É o mandado de segurança, previsto no art. 5º, LXIX, da Constituição Federal.',
             ARRAY['habeas corpus', 'ação popular', 'mandado de injunção', 'mandado de segurança', 'habeas data'], 4),
            (5, 'direito-administrativo', 'principios-da-administracao-publica',
             'nao-e-principio-expresso-no-caput-do-art-37-da-constituicao-federal',
             '**Não** é princípio expresso no *caput* do art. 37 da Constituição Federal:',
             'Os expressos são legalidade, impessoalidade, moralidade, publicidade e eficiência. A supremacia do interesse público é princípio implícito.',
             ARRAY['legalidade', 'impessoalidade', 'moralidade', 'supremacia do interesse público', 'eficiência'], 4),
            (6, 'informatica', 'planilhas-eletronicas',
             'em-uma-planilha-as-celulas-a1-a2-e-a3-contem-2-4-e-6',
             'Em uma planilha, as células A1, A2 e A3 contêm 2, 4 e 6. O resultado de `=MÉDIA(A1:A3)` é:',
             'A média é (2 + 4 + 6) ÷ 3 = 4.',
             ARRAY['2', '3', '4', '6', '12'], 3),
            (7, 'raciocinio-logico', 'logica-proposicional',
             'se-chove-entao-a-rua-fica-molhada-a-rua-nao-esta-molhada-logo',
             'Se chove, então a rua fica molhada. A rua não está molhada. Logo:',
             'Se a consequência (rua molhada) é falsa, a condição (chover) também é falsa. É a regra da negação do consequente.',
             ARRAY['choveu', 'não choveu', 'a rua vai secar', 'pode ter chovido', 'a rua está limpa'], 2),
            (8, 'raciocinio-logico', 'porcentagem',
             'um-produto-de-r-200-recebe-20-de-desconto-e-em-seguida-10-de-acrescimo',
             'Um produto de R$ 200 recebe 20% de desconto e, em seguida, 10% de acréscimo. O preço final é:',
             '200 × 0,80 = 160 e 160 × 1,10 = 176. Os percentuais não se anulam, porque incidem sobre bases diferentes.',
             ARRAY['R$ 170', 'R$ 176', 'R$ 180', 'R$ 184', 'R$ 220'], 2)
        ) AS v(ordem, materia, assunto, slug, enunciado, comentario, alternativas, correta)
        ORDER BY ordem
    LOOP
        CONTINUE WHEN EXISTS (SELECT 1 FROM questoes WHERE enunciado = q.enunciado);

        INSERT INTO questoes (id_materia, id_assunto, id_autor, tipo, origem, enunciado, comentario, slug,
                              status, publicado_em)
        SELECT m.id_materia, a.id_assunto, v_autor, 'MULTIPLA_ESCOLHA', 'AUTORAL', q.enunciado, q.comentario,
               q.slug, 'PUBLICADA', now()
        FROM materias m
        JOIN assuntos a ON a.id_materia = m.id_materia AND a.slug = q.assunto
        WHERE m.slug = q.materia
        RETURNING id_questao INTO v_questao;

        -- "correta" e a posicao (1 = A) da alternativa certa
        FOR i IN 1 .. array_length(q.alternativas, 1) LOOP
            INSERT INTO alternativas (id_questao, letra, texto, correta)
            VALUES (v_questao, substr('ABCDE', i, 1), q.alternativas[i], i = q.correta);
        END LOOP;
    END LOOP;
END
$seed$;

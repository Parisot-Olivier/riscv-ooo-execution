# Preuves

Cette section recense les preuves et invariants utilisés pour vérifier que certaines parties non triviales du système fonctionnent correctement et ne peuvent pas conduire à des états incohérents ou à des bugs.

L'objectif de ce document n'est pas de démontrer formellement l'ensemble du processeur, mais de documenter les propriétés importantes sur lesquelles repose l'implémentation. La preuve de l'ensemble du processeur fera l'objet d'une étude à part.

Il existe deux niveaux de preuves :

Les preuves simples : elles concernent des vérifications locales et directes, par exemple l’ajout d’un cycle de pipeline et la vérification que celui-ci n’introduit aucun décalage incorrect dans les signaux associés. Dans ce cas, la preuve est documentée directement dans le code sous la forme d’un commentaire précédé de la mention `// section preuve:`.

Les preuves complexes : lorsqu’une propriété nécessite un raisonnement plus détaillé, sa démonstration est recensée dans ce document. Le code concerné contient alors une référence vers le numéro de la preuve correspondante, également précédée de la mention `// section preuve:`.

## Preuve 1 — Exclusion mutuelle entre incrément et libération d'un registre physique

Le code concerné est le suivant :

```scala
when (
    (inc_annouced_read_src1 === i && valid_inc_src1) ||
    (inc_annouced_read_src2 === i && valid_inc_src2)
) {
    announced_read_counter(i) := announced_read_counter(i) + 1.U

}.elsewhen (free_reg(i)) {
    announced_read_counter(i) := 0.U
}
```

Il faut montrer qu'un même registre physique `i` ne peut pas, au cours du même cycle :

1. recevoir l'annonce d'une nouvelle lecture, et donc incrémenter `announced_read_counter(i)` ;
2. être libéré par `free_reg(i)`.

Autrement dit, l'état suivant doit être impossible :

```text
assert !(
    (
        (inc_annouced_read_src1 == i && valid_inc_src1) ||
        (inc_annouced_read_src2 == i && valid_inc_src2)
    )
    &&
    free_reg(i)
)
```

Cette propriété découle de la gestion du renommage des registres physiques.

`free_reg(i)` n'est émis que lorsque le registre physique `i` ne correspond plus à un registre architectural actif et peut effectivement être rendu à l'allocateur.

À l'inverse, lorsqu'une nouvelle instruction annonce une lecture de `i` via `src1` ou `src2`, ce registre physique appartient nécessairement à la chaîne de dépendance obtenue lors du renommage de cette instruction.

Une fois qu'un mapping architectural a été remplacé par un nouveau registre physique, les instructions renommées ultérieurement utilisent ce **nouveau mapping**. Elles ne peuvent donc plus créer de nouvelle dépendance RAW vers l'ancien registre physique.

L'ancien registre devient ainsi **hors du scope des nouvelles chaînes de dépendances** : aucune instruction renommée après son remplacement ne peut à nouveau annoncer une lecture sur celui-ci.

Par conséquent, au moment où `free_reg(i)` peut être émis, aucune nouvelle lecture de `i` ne peut simultanément être annoncée par :

```text
inc_annouced_read_src1
```

ou :

```text
inc_annouced_read_src2
```

On obtient donc l'invariant :

```text
free_reg(i)
    =>
!(
    (inc_annouced_read_src1 == i && valid_inc_src1) ||
    (inc_annouced_read_src2 == i && valid_inc_src2)
)
```

Les deux branches du `when / elsewhen` sont donc **mutuellement exclusives par construction** :

* soit une nouvelle lecture de `i` est annoncée, et `announced_read_counter(i)` est incrémenté ;
* soit `i` est libéré, et `announced_read_counter(i)` est remis à zéro.

Il n'existe pas de cas valide dans lequel ces deux opérations doivent être réalisées simultanément sur le même registre physique.

## Preuve 2 — Débordement de `counter_announced`

Il n'est pas nécessaire d'ajouter une logique particulière pour gérer explicitement le débordement de `counter_announced`.

La condition de libération du scheduler contient notamment :

```text
counter_announced == counter_served
```

Les compteurs peuvent donc être considérés comme des **compteurs cycliques**.

Si `counter_announced` atteint sa valeur maximale puis reçoit un incrément de `1`, il revient naturellement à `0`. `counter_served`, lui, ne revient pas simultanément à `0` sauf s'il a réellement parcouru le même nombre d'éléments.

Le fonctionnement est donc comparable au couple `ptr_top` / `ptr_end` d'une FIFO circulaire : le débordement binaire du compteur n'est pas un problème en lui-même tant qu'il est impossible que l'écart entre les deux compteurs atteigne un tour complet de l'espace représentable.

La propriété nécessaire est donc :

```text
2^ceil(log2(Size_counter))
    > Number_fifo * Number_entries_fifo
```

Cette contrainte garantit que le nombre maximal d'éléments pouvant être annoncés mais pas encore servis reste inférieur ou égal à la capacité représentable par le compteur.

Il devient alors impossible que `counter_announced` rattrape `counter_served` uniquement à cause d'un débordement et produise ainsi une fausse égalité.

La condition :

```text
counter_announced == counter_served
```

reste donc suffisante pour déterminer que tous les éléments annoncés ont effectivement été servis.

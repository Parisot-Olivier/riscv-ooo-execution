# Choix du langage pour la conception du processeur

Avant de commencer l’implémentation du processeur, j’ai consacré une première phase du projet au choix du langage de description matérielle. Ce choix est particulièrement important pour une architecture **Out-of-Order**, dans laquelle le nombre de modules, de transactions et de signaux de contrôle augmente rapidement.

L’objectif n’était donc pas uniquement de choisir un langage capable de décrire le matériel, mais de trouver une approche permettant de conserver une architecture **lisible, hiérarchisée et maintenable** à mesure que le processeur gagne en complexité.

## Verilog : rapidement écarté

Verilog a été écarté assez tôt.

Mon expérience lors de mon TIPE m’avait déjà montré ses limites lorsque le nombre de signaux entre modules commence à augmenter. Sur une architecture relativement simple, il reste parfaitement utilisable, mais la gestion manuelle d’un grand nombre de ports devient rapidement difficile à maintenir.

Un processeur Out-of-Order amplifie fortement ce problème. Une simple transaction entre deux unités peut transporter de nombreuses informations :

* l’opérande ou le résultat ;
* le registre architectural ou physique associé ;
* un identifiant d’instruction ;
* des informations de contrôle ;
* des indicateurs d’exception ;
* des signaux de validité ;
* des signaux de disponibilité ou de back-pressure.

Manipuler individuellement chacun de ces signaux aurait considérablement alourdi le code.

## SystemVerilog : une amélioration importante

SystemVerilog constitue une réponse beaucoup plus adaptée à ce problème.

Parmi ses fonctionnalités, deux m’intéressaient particulièrement :

* les `struct packed`, qui permettent de regrouper plusieurs signaux appartenant à une même transaction ;
* les `interface`, qui permettent d’encapsuler les communications entre plusieurs modules.

Ce sont précisément des fonctionnalités que j’aurais aimé avoir à ma disposition durant mon TIPE.

Une transaction peut par exemple être décrite sous la forme :

```systemverilog
typedef struct packed {
    logic [31:0] data;
    logic [5:0]  physical_register;
    logic [7:0]  instruction_id;
    logic        valid;
} transaction_t;
```


## Tentative d'une méthodologie « Chisel » en SystemVerilog

Avant d’abandonner SystemVerilog, j’ai essayé de construire une méthodologie permettant de retrouver une partie de la structuration offerte par Chisel.

L’idée était de créer des interfaces contenant les différentes transactions utilisées par un module :

```systemverilog
interface example_interface;

    typedef struct packed {
        transaction1_t transaction1;
        transaction2_t transaction2;
    } request_t;

    // ...

    modport module1 (...);
    modport module2 (...);

endinterface
```
## Structuration des signaux internes

J’avais également adopté une convention spécifique pour les signaux internes aux modules.

Au lieu de déclarer tous les signaux dans un même espace de noms, je les regroupais selon les différentes étapes de traitement :

```systemverilog
struct {
    struct {
        // signaux produits par always_ff1
        // et consommés par always_ff2
    } always_ff1_to_always_ff2;

    // ...
} internal;
```

Ici, `internal` était volontairement une structure non `packed`.

Son rôle n’était pas de représenter une structure matérielle particulière. Il s’agissait uniquement d'un outil de **hiérarchisation du code**.

Cela permettait notamment d’écrire :

```systemverilog
internal.rename_to_dispatch.valid
internal.rename_to_dispatch.destination
internal.rename_to_dispatch.rob_id
```

plutôt que :

```systemverilog
rename_to_dispatch_valid
rename_to_dispatch_destination
rename_to_dispatch_rob_id
```

Je n’utilisais volontairement pas de `typedef` pour cette structure : chaque module possède ses propres signaux internes et donc sa propre définition de `internal`.

Cette convention améliorait sensiblement la lisibilité du code, mais elle mettait également en évidence le besoin d’un langage permettant de manipuler plus naturellement des structures matérielles complexes.

## Le problème des interfaces et des handshakes

Les communications ne sont généralement pas unidirectionnelles.

Le producteur envoie des données et un signal de validité, tandis que le consommateur retourne un signal indiquant sa capacité à accepter cette transaction.

Avec SystemVerilog, les `struct packed` regroupent efficacement les données, mais la notion de direction appartient toujours à l'interface ou aux ports du module. Une même structure ne peut donc pas, à elle seule, représenter naturellement une transaction contenant simultanément des signaux allant dans les deux directions.

Il devient alors nécessaire de multiplier les interfaces, les `modport`, ou les conventions de nommage.

## Étude de Chisel

C’est dans ce contexte que j’ai étudié **Chisel**.

Chisel est un langage de construction matérielle basé sur Scala. Contrairement à SystemVerilog, il ne cherche pas seulement à décrire directement du RTL : le programme Scala construit une représentation du circuit qui sera ensuite transformée en RTL.

L’un des éléments ayant motivé cette étude est l’existence de **BOOM (Berkeley Out-of-Order Machine)**, un cœur RISC-V Out-of-Order développé en Chisel.

Il constitue une démonstration particulièrement intéressante de la capacité de Chisel à gérer une microarchitecture complexe.

Par ailleurs, mon expérience avec des langages fonctionnels, notamment **OCaml**, m’a permis d'appréhender relativement rapidement plusieurs concepts utilisés dans Chisel : construction de structures, composition, fonctions de génération et manipulation de types.

## Ce que Chisel apporte

L’un des avantages qui m’a le plus intéressé est la possibilité de représenter une interface comme un objet matériel.

Par exemple :

```scala
class Request extends Bundle {
    val address = UInt(32.W)
    val data    = UInt(32.W)
}
```

Ces structures peuvent ensuite être composées :

```scala
class ExecuteInterface extends Bundle {
    val request  = Decoupled(new Request)
    val response = Flipped(Decoupled(new Response))
}
```

La direction des communications devient alors directement visible dans la structure.

Les primitives comme `Decoupled` fournissent également une convention standard pour les communications de type `ready/valid`, ce qui évite de reconstruire manuellement le même protocole à chaque liaison.

## Une réserve : l’éloignement du RTL

Chisel m’a néanmoins initialement laissé une réserve importante : son **éloignement apparent du hardware**.

Avec SystemVerilog, la correspondance entre le code écrit et le matériel généré est généralement très directe. Lorsqu’un registre, un multiplexeur ou une logique combinatoire est décrit, il est relativement facile d’imaginer immédiatement la structure matérielle correspondante.

Chisel ajoute une couche supplémentaire : le code Scala génère une représentation matérielle, qui produit ensuite du RTL.

Chaque abstraction introduite doit rester associée à une représentation matérielle clairement identifiable : registres, multiplexeurs, mémoires, arbitres, files, réseaux de forwarding ou logique combinatoire.

## Décision

Après cette étude, j’ai finalement décidé d’écrire le processeur en **Chisel**.

Chisel devrait notamment permettre de réduire la quantité de code consacrée uniquement au transport et à l’organisation des signaux, afin que la structure du code reflète davantage la structure conceptuelle du processeur.

La suite du projet permettra de vérifier si ce choix reste pertinent lorsque l’architecture atteindra ses parties les plus complexes.

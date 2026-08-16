# Choice of language for processor design

Before starting the implementation of the processor, I dedicated an initial phase of the project to choosing the hardware description language. This choice is particularly important for an **Out-of-Order** architecture, in which the number of modules, transactions, and control signals increases rapidly.

The objective was therefore not only to choose a language capable of describing hardware, but to find an approach that would allow the architecture to remain **readable, hierarchical, and maintainable** as the processor grows in complexity.

## Verilog: quickly ruled out

Verilog was ruled out fairly early.

My experience during my TIPE had already shown me its limitations when the number of signals between modules starts to increase. On a relatively simple architecture, it remains perfectly usable, but manually managing a large number of ports quickly becomes difficult to maintain.

An Out-of-Order processor greatly amplifies this problem. A simple transaction between two units can carry a large amount of information:

* the operand or result;
* the associated architectural or physical register;
* an instruction identifier;
* control information;
* exception indicators;
* validity signals;
* availability or back-pressure signals.

Handling each of these signals individually would have made the code considerably heavier.

## SystemVerilog: a significant improvement

SystemVerilog provides a much more suitable answer to this problem.

Among its features, two were of particular interest to me:

* `struct packed`, which makes it possible to group several signals belonging to the same transaction;
* `interface`, which makes it possible to encapsulate communications between several modules.

These are precisely features that I would have liked to have had available during my TIPE.

A transaction can, for example, be described as follows:

```systemverilog
typedef struct packed {
    logic [31:0] data;
    logic [5:0]  physical_register;
    logic [7:0]  instruction_id;
    logic        valid;
} transaction_t;
```

## Attempt at a “Chisel” methodology in SystemVerilog

Before giving up on SystemVerilog, I tried to build a methodology that would reproduce some of the structuring offered by Chisel.

The idea was to create interfaces containing the different transactions used by a module:

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

## Structuring internal signals

I had also adopted a specific convention for signals internal to modules.

Instead of declaring all signals in the same namespace, I grouped them according to the different processing stages:

```systemverilog
struct {
    struct {
        // signals produced by always_ff1
        // and consumed by always_ff2
    } always_ff1_to_always_ff2;

    // ...
} internal;
```

Here, `internal` was deliberately a non-`packed` structure.

Its role was not to represent a particular hardware structure. It was only a tool for **structuring the code hierarchically**.

This made it possible, for example, to write:

```systemverilog
internal.rename_to_dispatch.valid
internal.rename_to_dispatch.destination
internal.rename_to_dispatch.rob_id
```

rather than:

```systemverilog
rename_to_dispatch_valid
rename_to_dispatch_destination
rename_to_dispatch_rob_id
```

I deliberately did not use a `typedef` for this structure: each module has its own internal signals and therefore its own definition of `internal`.

This convention significantly improved code readability, but it also highlighted the need for a language that makes it possible to manipulate complex hardware structures more naturally.

## The problem of interfaces and handshakes

Communications are generally not unidirectional.

The producer sends data and a validity signal, while the consumer returns a signal indicating its ability to accept that transaction.

With SystemVerilog, `struct packed` efficiently groups data, but the notion of direction still belongs to the interface or to the module ports. A single structure therefore cannot, by itself, naturally represent a transaction containing signals travelling simultaneously in both directions.

It then becomes necessary to multiply interfaces, `modport`s, or naming conventions.

## Studying Chisel

It was in this context that I studied **Chisel**.

Chisel is a hardware construction language based on Scala. Unlike SystemVerilog, it does not only aim to directly describe RTL: the Scala program builds a representation of the circuit, which is then transformed into RTL.

One of the elements that motivated this study was the existence of **BOOM (Berkeley Out-of-Order Machine)**, an Out-of-Order RISC-V core developed in Chisel.

It provides a particularly interesting demonstration of Chisel’s ability to handle a complex microarchitecture.

In addition, my experience with functional languages, particularly **OCaml**, allowed me to understand several concepts used in Chisel relatively quickly: structure construction, composition, generation functions, and type manipulation.

## What Chisel provides

One of the advantages that interested me the most is the possibility of representing an interface as a hardware object.

For example:

```scala
class Request extends Bundle {
    val address = UInt(32.W)
    val data    = UInt(32.W)
}
```

These structures can then be composed:

```scala
class ExecuteInterface extends Bundle {
    val request  = Decoupled(new Request)
    val response = Flipped(Decoupled(new Response))
}
```

The direction of communications then becomes directly visible in the structure.

Primitives such as `Decoupled` also provide a standard convention for `ready/valid` communications, which avoids manually rebuilding the same protocol for every connection.

## One reservation: distance from RTL

Chisel nevertheless initially left me with one significant reservation: its **apparent distance from hardware**.

With SystemVerilog, the correspondence between the code being written and the generated hardware is generally very direct. When a register, multiplexer, or combinational logic is described, it is relatively easy to immediately imagine the corresponding hardware structure.

Chisel adds an additional layer: the Scala code generates a hardware representation, which then produces RTL.

Each abstraction introduced must remain associated with a clearly identifiable hardware representation: registers, multiplexers, memories, arbiters, queues, forwarding networks, or combinational logic.

## Decision

After this study, I ultimately decided to write the processor in **Chisel**.

Chisel should notably make it possible to reduce the amount of code dedicated solely to transporting and organizing signals, so that the structure of the code more closely reflects the conceptual structure of the processor.

The remainder of the project will make it possible to determine whether this choice remains relevant when the architecture reaches its most complex parts.

# riscv-ooo-execution
Chisel implementation of the execution backend for a 64-bit RISC-V out-of-order processor.

Work in Progress

I am currently developing the execution engine of a 64-bit RISC-V out-of-order processor, implemented in Chisel. This project focuses exclusively on the execution backend, including register renaming, instruction scheduling, issue logic, execution units, write-back, and retirement. The instruction fetch and decode stages are intentionally out of scope.

The implementation targets the RISC-V RV64 architecture and serves as an educational and experimental platform for exploring modern out-of-order processor microarchitecture and hardware design.

This architecture is stable, but some signal names and the location of certain information may change slightly during implementation. See the language selection document for an explanation of why Chisel was chosen:
[in english](choice_of_language_en.md); [in french](choice_of_language_fr.md)

![](schema_pipeline_global.jpg)

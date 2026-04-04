OUTPUT_ARCH("riscv")
ENTRY(_start)

MEMORY {
  RAM (rwx) : ORIGIN = 0x80000000, LENGTH = 8M
}

SECTIONS {
  . = ORIGIN(RAM);

  .text.init : {
    *(.text.init)
  } > RAM

  .text : ALIGN(16) {
    *(.text .text.*)
  } > RAM

  .rodata : ALIGN(16) {
    *(.rodata .rodata.*)
  } > RAM

  .tohost ALIGN(0x1000) : {
    *(.tohost)
  } > RAM

  .data ALIGN(0x1000) : {
    *(.data .data.*)
  } > RAM

  .bss (NOLOAD) : ALIGN(16) {
    __bss_start = .;
    *(.bss .bss.*)
    *(COMMON)
    __bss_end = .;
  } > RAM

  .signature : ALIGN(16) {
    *(.signature)
  } > RAM

  _end = .;
  . = ALIGN(16);
  _stack_top = ORIGIN(RAM) + LENGTH(RAM);
}

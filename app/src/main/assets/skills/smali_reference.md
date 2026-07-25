# Smali Bytecode Reference

## Register Types
| Notation | Meaning |
|----------|---------|
| `v0`-`v15` | Local registers (method-scoped) |
| `p0`-`pN` | Parameter registers (p0 = `this` for non-static) |
| `.registers N` | Total registers (locals + params) |
| `.locals N` | Only local registers |

## Instruction Set

### Move Instructions
| Opcode | Mnemonic | Format |
|--------|----------|--------|
| 0x01 | `move vA, vB` | 12x |
| 0x04 | `move-wide vA, vB` | 12x |
| 0x07 | `move-object vA, vB` | 12x |
| 0x0A | `move-result vA` | 11x |
| 0x0C | `move-result-object vA` | 11x |

### Return Instructions
| Opcode | Mnemonic | Format |
|--------|----------|--------|
| 0x0E | `return-void` | 10x |
| 0x0F | `return vA` | 11x |
| 0x10 | `return-wide vA` | 11x |
| 0x11 | `return-object vA` | 11x |

### Const Instructions
| Opcode | Mnemonic | Format |
|--------|----------|--------|
| 0x12 | `const/4 vA, #+B` | 11n |
| 0x13 | `const/16 vA, #+BBBB` | 21s |
| 0x14 | `const vA, #+BBBBBBBB` | 31i |
| 0x15 | `const/high16 vA, #+BBBB0000` | 21h |
| 0x16 | `const-wide/16 vA, #+BBBB` | 21s |
| 0x17 | `const-wide/32 vA, #+BBBBBBBB` | 31i |
| 0x18 | `const-wide vA, #+BBBBBBBBBBBBBBBB` | 51l |
| 0x1A | `const-string vA, string@BBBB` | 21c |
| 0x1C | `const-class vA, type@BBBB` | 21c |

### Goto / Conditional
| Opcode | Mnemonic | Format |
|--------|----------|--------|
| 0x28 | `goto +AA` | 10t |
| 0x2E | `if-eq vA, vB, +CCCC` | 22t |
| 0x2F | `if-ne vA, vB, +CCCC` | 22t |
| 0x30 | `if-lt vA, vB, +CCCC` | 22t |
| 0x31 | `if-ge vA, vB, +CCCC` | 22t |
| 0x32 | `if-gt vA, vB, +CCCC` | 22t |
| 0x33 | `if-le vA, vB, +CCCC` | 22t |
| 0x38 | `if-eqz vA, +CCCC` | 21t |
| 0x39 | `if-nez vA, +CCCC` | 21t |

### Field Access
| Opcode | Mnemonic | Format |
|--------|----------|--------|
| 0x52 | `iget vA, vB, field@CCCC` | 22c |
| 0x53 | `iput vA, vB, field@CCCC` | 22c |
| 0x60 | `sget vA, field@BBBB` | 21c |
| 0x61 | `sput vA, field@BBBB` | 21c |

### Invoke Instructions
| Opcode | Mnemonic | Format |
|--------|----------|--------|
| 0x6E | `invoke-virtual {args}, method@BBBB` | 3rc |
| 0x6F | `invoke-super {args}, method@BBBB` | 3rc |
| 0x70 | `invoke-direct {args}, method@BBBB` | 3rc |
| 0x71 | `invoke-static {args}, method@BBBB` | 3rc |
| 0x72 | `invoke-interface {args}, method@BBBB` | 3rc |

## Common Patch Patterns

### Force boolean return true
```smali
const/4 v0, 0x1
return v0
```

### Force boolean return false
```smali
const/4 v0, 0x0
return v0
```

### NOP entire method
```smali
return-void
```

### Inject Log.d
```smali
const-string v0, "PatchMaster"
const-string v1, "Hooked!"
invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
```

### Type Signatures
| Java | Smali |
|------|-------|
| `int` | `I` |
| `long` | `J` |
| `float` | `F` |
| `double` | `D` |
| `boolean` | `Z` |
| `char` | `C` |
| `byte` | `B` |
| `short` | `S` |
| `void` | `V` |
| `String` | `Ljava/lang/String;` |
| `int[]` | `[I` |
| `Object` | `Ljava/lang/Object;` |
| `Context` | `Landroid/content/Context;` |

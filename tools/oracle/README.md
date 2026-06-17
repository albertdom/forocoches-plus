# Oráculo / golden test del filtrado (`content.js`)

Test de **regresión** del filtrado de `app/src/main/assets/content.js`. Carga snapshots
**congelados** de páginas reales de ForoCoches, inyecta el mismo puente `Android` que usa la
app (con inputs fijos de test), **ejecuta el `content.js` real** en un DOM (jsdom) y comprueba
que oculta **exactamente** lo esperado.

Si alguien rompe el filtrado al tocar `content.js`, este test falla en el mismo PR.

> No comprueba cambios de HTML de FC ("drift") — eso lo detecta el canario en el móvil del
> usuario, con su propia sesión. Aquí el HTML está congelado a propósito.

## Qué cubre

| Fixture | Superficie | Casos |
|---|---|---|
| `thread_new` / `thread_old` | hilo (`filterPosts`) | post de ignorado, **cita** a ignorado (skin nuevo), post normal (negativo) |
| `listing_new` / `listing_old` | listado (`filterThreads`) | hilo de **creador ignorado**, hilo con **keyword**, hilos normales (negativos) |

Inputs y salida esperada están en `golden.json` (ignorados: `GTX`, `Ibai_S`, `Jorge_Lega`;
keyword: `lexus`). En el listado del skin viejo móvil el creador no aparece en el HTML, así
que se mockea `getCachedCreator` con el mapa `creators` del golden (sacado del skin nuevo).

## Uso

```bash
cd tools/oracle
npm install      # solo la primera vez (instala jsdom)
npm test         # corre el oráculo (node --test)
```

## Actualizar los fixtures

Si FC cambia su HTML y hay que recapturar:
1. Captura las páginas (hilo + listado) en skin **nuevo** y **antiguo móvil** con sesión real
   (vía el proxy), y guarda el HTML crudo en `fixtures/` con los mismos nombres.
2. Reajusta `golden.json` si cambian los hilos/usuarios visibles (ids de hilo, autores, etc.).
3. `npm test` hasta verde.

El `content.js` se lee **en su sitio** (`app/src/main/assets/`), no se copia — por eso el
test valida siempre la versión actual del código.

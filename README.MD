# Cristal

App personal para organizar deudas y finanzas desde Android.

## Objetivo

Cristal debe ayudar a ver con claridad que deudas existen, cuales son mas importantes y que pagos vienen proximamente.

## MVP

1. Medir la prioridad de las deudas de la mas grande a la mas pequena.
2. Organizar deudas, pagos y notas financieras personales.
3. Mostrar resumen de dinero pendiente, dinero pagado y avance general.
4. Proteger la app con PIN local.
5. Crear recordatorios de pago.
6. Generar una APK instalable.

## Lenguaje y tecnologia

- Lenguaje: Kotlin.
- Plataforma: Android nativo.
- Interfaz: Jetpack Compose.
- Almacenamiento MVP: SharedPreferences con JSON local.
- Base de datos futura: Room / SQLite.
- Recordatorios MVP: AlarmManager + notificaciones locales.
- Recordatorios futuros: WorkManager para trabajos mas avanzados.
- Seguridad: PIN guardado de forma segura con hash local y bloqueo de pantalla al abrir la app.

## Pantallas principales

- Desbloqueo con PIN.
- Resumen financiero.
- Lista de deudas ordenadas por prioridad.
- Crear y editar deuda.
- Registrar pagos.
- Historial de pagos.
- Recordatorios.
- Configuracion.

## Datos principales

### Deuda

- Nombre.
- Monto original.
- Monto pagado.
- Monto pendiente.
- Fecha limite opcional.
- Nota opcional.
- Estado: activa, pagada o archivada.

### Pago

- Deuda asociada.
- Monto.
- Fecha.
- Nota opcional.

### Recordatorio

- Deuda asociada opcional.
- Fecha y hora.
- Repeticion opcional.
- Mensaje.

## Prioridad inicial

La primera version ordenara las deudas por monto pendiente, de mayor a menor.

Mas adelante se pueden agregar otros metodos:

- Bola de nieve: pagar primero la deuda mas pequena.
- Avalancha: pagar primero la deuda con mayor interes.
- Manual: ordenar segun preferencia personal.

## Roadmap

1. Crear proyecto Android en la carpeta `apk`. Hecho.
2. Crear modelos de datos y almacenamiento local. Hecho.
3. Construir pantallas basicas de deudas y pagos. Hecho.
4. Agregar calculos de resumen y prioridad. Hecho.
5. Agregar PIN. Hecho.
6. Agregar recordatorios y notificaciones. Hecho.
7. Generar APK de prueba. Pendiente de Android Studio / SDK.

## Como generar la APK

1. Instalar Android Studio.
2. Abrir la carpeta `apk` como proyecto Android.
3. Esperar a que Gradle descargue dependencias.
4. Conectar un telefono o iniciar un emulador.
5. Probar con Run.
6. Generar APK desde `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

La APK de prueba quedara normalmente en:

`apk/app/build/outputs/apk/debug/app-debug.apk`

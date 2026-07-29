package com.obra.certificaciones.documentacion.service;

import com.obra.certificaciones.deposito.service.DepositoService;
import com.obra.certificaciones.documentacion.dto.ContratistaDocumentacionResumen;
import com.obra.certificaciones.documentacion.dto.DocumentacionResumen;
import com.obra.certificaciones.documentacion.dto.DocumentoObraForm;
import com.obra.certificaciones.documentacion.dto.GrupoDocumentacionContratista;
import com.obra.certificaciones.documentacion.entity.CarpetaDocumentacion;
import com.obra.certificaciones.documentacion.entity.DocumentoObra;
import com.obra.certificaciones.documentacion.entity.EstadoDocumentoObra;
import com.obra.certificaciones.documentacion.entity.SujetoDocumental;
import com.obra.certificaciones.documentacion.entity.TipoVinculoDocumental;
import com.obra.certificaciones.documentacion.repository.CarpetaDocumentacionRepository;
import com.obra.certificaciones.documentacion.repository.DocumentoObraRepository;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.proveedor.service.ProveedorService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;

@Service
@RequiredArgsConstructor
public class DocumentacionService {
    private static final List<String> CARPETAS_INICIALES = List.of(
            "Enercon",
            "Miguel Muriel",
            "Coronel",
            "El Artesano",
            "Inceobras"
    );

    private final CarpetaDocumentacionRepository carpetaRepository;
    private final DocumentoObraRepository repository;
    private final ProveedorService proveedorService;
    private final DepositoService depositoService;

    @Value("${app.documentacion.upload-dir:uploads/documentacion}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public List<DocumentoObra> listar(Obra obra) {
        return repository.findByObraIdAndActivoTrueOrderByFechaVencimientoAscIdDesc(obra.getId());
    }

    @Transactional(readOnly = true)
    public Page<DocumentoObra> listar(Obra obra, Pageable pageable) {
        return repository.findByObraIdAndActivoTrueOrderByFechaVencimientoAscIdDesc(obra.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public DocumentacionResumen resumen(Obra obra) {
        List<DocumentoObra> documentos = listar(obra);
        return resumen(documentos, carpetasActivas(obra).stream()
                .filter(carpeta -> !carpeta.isGeneral())
                .map(CarpetaDocumentacion::getProveedor)
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    @Transactional
    public List<GrupoDocumentacionContratista> agruparPorContratista(Obra obra) {
        asegurarCarpetasIniciales(obra);
        List<DocumentoObra> documentos = listar(obra);
        Map<Long, List<DocumentoObra>> porProveedor = documentos.stream()
                .filter(documento -> documento.getProveedor() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        documento -> documento.getProveedor().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        List<DocumentoObra> generales = documentos.stream()
                .filter(documento -> documento.getProveedor() == null)
                .toList();
        List<GrupoDocumentacionContratista> grupos = new java.util.ArrayList<>();
        for (CarpetaDocumentacion carpeta : carpetasActivas(obra)) {
            if (carpeta.isGeneral()) {
                grupos.add(toGrupo(null, carpeta.getNombre(), generales, true));
            } else if (carpeta.getProveedor() != null) {
                Proveedor proveedor = carpeta.getProveedor();
                grupos.add(toGrupo(proveedor.getId(), carpeta.getNombre(), porProveedor.getOrDefault(proveedor.getId(), List.of()), false));
            }
        }
        return grupos.stream()
                .sorted(Comparator.comparing(GrupoDocumentacionContratista::vencidos).reversed()
                        .thenComparing(GrupoDocumentacionContratista::porVencer, Comparator.reverseOrder())
                        .thenComparing(GrupoDocumentacionContratista::pendientes, Comparator.reverseOrder())
                        .thenComparing(GrupoDocumentacionContratista::general)
                        .thenComparing(GrupoDocumentacionContratista::nombre))
                .toList();
    }

    @Transactional
    public CarpetaDocumentacion crearCarpetaContratista(Long proveedorId, Obra obra) {
        if (proveedorId == null) {
            throw new IllegalArgumentException("Debe seleccionar un contratista existente.");
        }
        Proveedor proveedor = proveedorService.obtener(proveedorId);
        if (carpetaRepository.existsByObraIdAndProveedorIdAndActivoTrue(obra.getId(), proveedor.getId())) {
            throw new IllegalArgumentException("Ese contratista ya tiene carpeta documental.");
        }
        CarpetaDocumentacion carpeta = new CarpetaDocumentacion();
        carpeta.setObra(obra);
        carpeta.setProveedor(proveedor);
        carpeta.setNombre(proveedor.getNombre());
        carpeta.setGeneral(false);
        return carpetaRepository.save(carpeta);
    }

    @Transactional
    public CarpetaDocumentacion crearCarpetaContratista(String nombreContratista, Obra obra) {
        if (!StringUtils.hasText(nombreContratista)) {
            throw new IllegalArgumentException("Debe indicar el nombre de un contratista existente.");
        }
        Proveedor proveedor = proveedorService.listarActivos().stream()
                .filter(candidato -> candidato.getNombre() != null
                        && candidato.getNombre().toLowerCase().contains(nombreContratista.trim().toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No encontre un contratista existente con ese nombre. Primero crealo en Proveedores."));
        return crearCarpetaContratista(proveedor.getId(), obra);
    }

    @Transactional(readOnly = true)
    public DocumentoObra obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el documento " + id));
    }

    @Transactional
    public DocumentoObra guardar(DocumentoObraForm form, Obra obra) {
        validar(form);
        DocumentoObra documento = form.getId() == null ? new DocumentoObra() : obtener(form.getId());
        documento.setObra(obra);
        documento.setSujeto(form.getSujeto());
        documento.setVinculo(form.getVinculo());
        documento.setTipo(form.getTipo());
        documento.setNombrePersonalizado(texto(form.getNombrePersonalizado()));
        documento.setVehiculoDominio(texto(form.getVehiculoDominio()));
        documento.setVehiculoDetalle(texto(form.getVehiculoDetalle()));
        documento.setFechaEmision(form.getFechaEmision());
        documento.setFechaVencimiento(form.getFechaVencimiento());
        documento.setFechaPresentacion(form.getFechaPresentacion());
        documento.setMensual(form.isMensual());
        documento.setObligatorioIngreso(form.isObligatorioIngreso());
        documento.setPresentado(form.isPresentado());
        documento.setImpresoLegajo(form.isImpresoLegajo());
        documento.setActivo(form.isActivo());
        documento.setReferenciaArchivo(texto(form.getReferenciaArchivo()));
        documento.setUbicacionFisica(texto(form.getUbicacionFisica()));
        documento.setObservacion(texto(form.getObservacion()));
        documento.setProveedor(form.getProveedorId() == null ? null : proveedorService.obtener(form.getProveedorId()));
        documento.setTrabajador(form.getTrabajadorId() == null ? null : depositoService.obtenerTrabajador(form.getTrabajadorId()));
        return repository.save(documento);
    }

    @Transactional
    public void eliminar(Long id) {
        DocumentoObra documento = obtener(id);
        documento.setActivo(false);
        repository.save(documento);
    }

    @Transactional
    public DocumentoObra adjuntarPdf(Long id, MultipartFile archivo) throws IOException {
        DocumentoObra documento = obtener(id);
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un PDF para adjuntar.");
        }
        String nombreOriginal = archivo.getOriginalFilename() == null ? "documento.pdf" : archivo.getOriginalFilename();
        if (!nombreOriginal.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Solo se permiten archivos PDF.");
        }
        Path carpeta = carpetaAdjuntos();
        Files.createDirectories(carpeta);
        String nombreSeguro = "documento-" + id + "-" + System.currentTimeMillis() + "-" + limpiarNombreArchivo(nombreOriginal);
        Path destino = carpeta.resolve(nombreSeguro).normalize();
        if (!destino.startsWith(carpeta)) {
            throw new IllegalArgumentException("Nombre de archivo invalido.");
        }
        archivo.transferTo(destino);
        documento.setReferenciaArchivo(nombreSeguro);
        documento.setPresentado(true);
        if (documento.getFechaPresentacion() == null) {
            documento.setFechaPresentacion(LocalDate.now());
        }
        return repository.save(documento);
    }

    @Transactional(readOnly = true)
    public Resource archivo(Long id) throws MalformedURLException {
        DocumentoObra documento = obtener(id);
        if (!StringUtils.hasText(documento.getReferenciaArchivo())) {
            throw new EntityNotFoundException("El documento no tiene archivo adjunto.");
        }
        Path archivo = carpetaAdjuntos().resolve(documento.getReferenciaArchivo()).normalize();
        if (!archivo.startsWith(carpetaAdjuntos()) || !Files.exists(archivo)) {
            throw new EntityNotFoundException("No se encontro el archivo adjunto.");
        }
        return new UrlResource(archivo.toUri());
    }

    @Transactional(readOnly = true)
    public DocumentoObraForm formDesde(DocumentoObra documento) {
        DocumentoObraForm form = new DocumentoObraForm();
        form.setId(documento.getId());
        form.setProveedorId(documento.getProveedor() == null ? null : documento.getProveedor().getId());
        form.setTrabajadorId(documento.getTrabajador() == null ? null : documento.getTrabajador().getId());
        form.setSujeto(documento.getSujeto());
        form.setVinculo(documento.getVinculo());
        form.setTipo(documento.getTipo());
        form.setNombrePersonalizado(documento.getNombrePersonalizado());
        form.setVehiculoDominio(documento.getVehiculoDominio());
        form.setVehiculoDetalle(documento.getVehiculoDetalle());
        form.setFechaEmision(documento.getFechaEmision());
        form.setFechaVencimiento(documento.getFechaVencimiento());
        form.setFechaPresentacion(documento.getFechaPresentacion());
        form.setMensual(documento.isMensual());
        form.setObligatorioIngreso(documento.isObligatorioIngreso());
        form.setPresentado(documento.isPresentado());
        form.setImpresoLegajo(documento.isImpresoLegajo());
        form.setActivo(documento.isActivo());
        form.setReferenciaArchivo(documento.getReferenciaArchivo());
        form.setUbicacionFisica(documento.getUbicacionFisica());
        form.setObservacion(documento.getObservacion());
        return form;
    }

    public DocumentacionResumen resumen(List<DocumentoObra> documentos) {
        return resumen(documentos, List.of());
    }

    public DocumentacionResumen resumen(List<DocumentoObra> documentos, List<Proveedor> proveedores) {
        long aptos = 0, porVencer = 0, vencidos = 0, pendientes = 0, mensualesPendientes = 0;
        Map<String, MutableContratista> porContratista = new LinkedHashMap<>();
        LocalDate hoy = LocalDate.now();
        for (DocumentoObra documento : documentos) {
            EstadoDocumentoObra estado = documento.estado();
            if (estado == EstadoDocumentoObra.APTO) aptos++;
            if (estado == EstadoDocumentoObra.POR_VENCER) porVencer++;
            if (estado == EstadoDocumentoObra.VENCIDO) vencidos++;
            if (estado == EstadoDocumentoObra.PENDIENTE) pendientes++;
            if (documento.isMensual() && (documento.getFechaPresentacion() == null
                    || documento.getFechaPresentacion().getMonth() != hoy.getMonth()
                    || documento.getFechaPresentacion().getYear() != hoy.getYear())) {
                mensualesPendientes++;
            }
            String contratista = documento.getProveedor() == null ? "Sin contratista" : documento.getProveedor().getNombre();
            MutableContratista resumen = porContratista.computeIfAbsent(contratista, key -> new MutableContratista());
            resumen.total++;
            if (estado == EstadoDocumentoObra.VENCIDO) resumen.vencidos++;
            if (estado == EstadoDocumentoObra.POR_VENCER) resumen.porVencer++;
            if (estado == EstadoDocumentoObra.PENDIENTE) resumen.pendientes++;
        }
        for (Proveedor proveedor : proveedores) {
            porContratista.computeIfAbsent(proveedor.getNombre(), key -> new MutableContratista());
        }
        List<ContratistaDocumentacionResumen> contratistas = porContratista.entrySet().stream()
                .map(entry -> entry.getValue().toResumen(entry.getKey()))
                .sorted(Comparator.comparing(ContratistaDocumentacionResumen::vencidos).reversed()
                        .thenComparing(ContratistaDocumentacionResumen::porVencer).reversed())
                .toList();
        return new DocumentacionResumen(documentos.size(), aptos, porVencer, vencidos, pendientes, mensualesPendientes, contratistas);
    }

    private void validar(DocumentoObraForm form) {
        if (form.getSujeto() == null) {
            throw new IllegalArgumentException("Debe indicar si corresponde a contratista, persona, vehiculo u obra.");
        }
        if (form.getTipo() == null) {
            throw new IllegalArgumentException("El tipo de documento es obligatorio.");
        }
        if (form.getVinculo() != null && !form.getTipo().aplicaA(form.getVinculo())) {
            throw new IllegalArgumentException("Ese tipo de documento no corresponde al vinculo seleccionado.");
        }
        if (form.getSujeto() == SujetoDocumental.CONTRATISTA && form.getProveedorId() == null) {
            throw new IllegalArgumentException("Debe seleccionar un contratista/proveedor.");
        }
        if (form.getSujeto() == SujetoDocumental.PERSONA && form.getTrabajadorId() == null) {
            throw new IllegalArgumentException("Debe seleccionar una persona.");
        }
        if (form.getSujeto() == SujetoDocumental.VEHICULO && !StringUtils.hasText(form.getVehiculoDominio())) {
            throw new IllegalArgumentException("Debe indicar dominio o identificacion del vehiculo/maquinaria.");
        }
    }

    private String texto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private Path carpetaAdjuntos() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private String limpiarNombreArchivo(String nombre) {
        String normalizado = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9._-]", "-")
                .replaceAll("-+", "-");
        return normalizado.isBlank() ? "documento.pdf" : normalizado;
    }

    private List<CarpetaDocumentacion> carpetasActivas(Obra obra) {
        return carpetaRepository.findByObraIdAndActivoTrueOrderByGeneralDescNombreAsc(obra.getId());
    }

    private void asegurarCarpetasIniciales(Obra obra) {
        if (carpetaRepository.findByObraIdAndGeneralTrueAndActivoTrue(obra.getId()).isEmpty()) {
            CarpetaDocumentacion general = new CarpetaDocumentacion();
            general.setObra(obra);
            general.setNombre("Simende (Obra)");
            general.setGeneral(true);
            carpetaRepository.save(general);
        }
        for (String nombreBase : CARPETAS_INICIALES) {
            buscarProveedorParaCarpeta(nombreBase).ifPresent(proveedor -> {
                if (!carpetaRepository.existsByObraIdAndProveedorIdAndActivoTrue(obra.getId(), proveedor.getId())) {
                    CarpetaDocumentacion carpeta = new CarpetaDocumentacion();
                    carpeta.setObra(obra);
                    carpeta.setProveedor(proveedor);
                    carpeta.setNombre(nombreBase.equals("El Artesano") ? "El Artesano (Gomez Eliana)" : proveedor.getNombre());
                    carpeta.setGeneral(false);
                    carpetaRepository.save(carpeta);
                }
            });
        }
    }

    private java.util.Optional<Proveedor> buscarProveedorParaCarpeta(String nombreBase) {
        String clave = nombreBase.toLowerCase();
        return proveedorService.listarActivos().stream()
                .filter(proveedor -> {
                    String nombre = proveedor.getNombre() == null ? "" : proveedor.getNombre().toLowerCase();
                    if (clave.equals("el artesano")) {
                        return nombre.contains("artesano") || nombre.contains("gomez eliana") || nombre.contains("gómez eliana");
                    }
                    return nombre.contains(clave.toLowerCase());
                })
                .findFirst();
    }

    private GrupoDocumentacionContratista toGrupo(Long proveedorId, String nombre, List<DocumentoObra> documentos, boolean general) {
        long aptos = 0, porVencer = 0, vencidos = 0, pendientes = 0;
        for (DocumentoObra documento : documentos) {
            EstadoDocumentoObra estado = documento.estado();
            if (estado == EstadoDocumentoObra.APTO) aptos++;
            if (estado == EstadoDocumentoObra.POR_VENCER) porVencer++;
            if (estado == EstadoDocumentoObra.VENCIDO) vencidos++;
            if (estado == EstadoDocumentoObra.PENDIENTE) pendientes++;
        }
        EstadoDocumentoObra estadoGrupo = EstadoDocumentoObra.APTO;
        if (documentos.isEmpty()) estadoGrupo = EstadoDocumentoObra.PENDIENTE;
        else if (vencidos > 0) estadoGrupo = EstadoDocumentoObra.VENCIDO;
        else if (pendientes > 0) estadoGrupo = EstadoDocumentoObra.PENDIENTE;
        else if (porVencer > 0) estadoGrupo = EstadoDocumentoObra.POR_VENCER;
        TipoVinculoDocumental vinculoPrincipal = vinculoPrincipal(documentos);
        return new GrupoDocumentacionContratista(
                proveedorId,
                nombre,
                estadoGrupo,
                documentos.size(),
                aptos,
                porVencer,
                vencidos,
                pendientes,
                general ? "is-general" : cssVinculo(vinculoPrincipal),
                general ? "General" : vinculoPrincipal.getDescripcion(),
                general,
                documentos
        );
    }

    private TipoVinculoDocumental vinculoPrincipal(List<DocumentoObra> documentos) {
        boolean relacion = documentos.stream().anyMatch(documento -> documento.getVinculo() == TipoVinculoDocumental.RELACION_DEPENDENCIA);
        boolean monotributo = documentos.stream().anyMatch(documento -> documento.getVinculo() == TipoVinculoDocumental.MONOTRIBUTISTA);
        boolean visita = documentos.stream().anyMatch(documento -> documento.getVinculo() == TipoVinculoDocumental.VISITA_PROVEEDOR
                || documento.getVinculo() == TipoVinculoDocumental.VISITA_MONOTRIBUTISTA);
        boolean vehiculo = documentos.stream().anyMatch(documento -> documento.getVinculo() == TipoVinculoDocumental.VEHICULO_MAQUINARIA);
        if (relacion) return TipoVinculoDocumental.RELACION_DEPENDENCIA;
        if (monotributo) return TipoVinculoDocumental.MONOTRIBUTISTA;
        if (visita) return TipoVinculoDocumental.VISITA_PROVEEDOR;
        if (vehiculo) return TipoVinculoDocumental.VEHICULO_MAQUINARIA;
        return TipoVinculoDocumental.GENERAL;
    }

    private String cssVinculo(TipoVinculoDocumental vinculo) {
        return switch (vinculo) {
            case RELACION_DEPENDENCIA -> "is-relacion";
            case MONOTRIBUTISTA -> "is-monotributo";
            case VISITA_PROVEEDOR, VISITA_MONOTRIBUTISTA -> "is-visita";
            case VEHICULO_MAQUINARIA -> "is-vehiculo";
            case GENERAL -> "is-general";
        };
    }

    private static class MutableContratista {
        long total;
        long vencidos;
        long porVencer;
        long pendientes;

        ContratistaDocumentacionResumen toResumen(String nombre) {
            EstadoDocumentoObra estado = EstadoDocumentoObra.APTO;
            if (total == 0) estado = EstadoDocumentoObra.PENDIENTE;
            else if (vencidos > 0) estado = EstadoDocumentoObra.VENCIDO;
            else if (pendientes > 0) estado = EstadoDocumentoObra.PENDIENTE;
            else if (porVencer > 0) estado = EstadoDocumentoObra.POR_VENCER;
            return new ContratistaDocumentacionResumen(nombre, total, vencidos, porVencer, pendientes, estado);
        }
    }
}

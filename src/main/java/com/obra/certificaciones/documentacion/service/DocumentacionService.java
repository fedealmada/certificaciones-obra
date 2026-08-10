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
        Map<Long, List<DocumentoObra>> porCarpeta = documentos.stream()
                .filter(documento -> documento.getCarpeta() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        documento -> documento.getCarpeta().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Map<Long, List<DocumentoObra>> porProveedor = documentos.stream()
                .filter(documento -> documento.getCarpeta() == null && documento.getProveedor() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        documento -> documento.getProveedor().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        List<DocumentoObra> generales = documentos.stream()
                .filter(documento -> documento.getCarpeta() == null && documento.getProveedor() == null)
                .toList();
        List<GrupoDocumentacionContratista> grupos = new java.util.ArrayList<>();
        for (CarpetaDocumentacion carpeta : carpetasActivas(obra)) {
            List<DocumentoObra> documentosCarpeta = porCarpeta.get(carpeta.getId());
            if (carpeta.isGeneral()) {
                grupos.add(toGrupo(carpeta, null, documentosCarpeta == null ? generales : documentosCarpeta, true));
            } else if (carpeta.getProveedor() != null) {
                Proveedor proveedor = carpeta.getProveedor();
                grupos.add(toGrupo(carpeta, proveedor.getId(), documentosCarpeta == null ? porProveedor.getOrDefault(proveedor.getId(), List.of()) : documentosCarpeta, false));
            } else {
                grupos.add(toGrupo(carpeta, null, documentosCarpeta == null ? List.of() : documentosCarpeta, false));
            }
        }
        return grupos;
    }
    @Transactional
    public GrupoDocumentacionContratista obtenerGrupoCarpeta(Long carpetaId, Obra obra) {
        return agruparPorContratista(obra).stream()
                .filter(grupo -> grupo.carpetaId() != null && grupo.carpetaId().equals(carpetaId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("No existe la carpeta documental " + carpetaId));
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
        validarNombreCarpetaUnico(obra, proveedor.getNombre(), null);
        CarpetaDocumentacion carpeta = new CarpetaDocumentacion();
        int orden = siguienteOrdenCarpeta(obra);
        carpeta.setObra(obra);
        carpeta.setProveedor(proveedor);
        carpeta.setNombre(proveedor.getNombre());
        carpeta.setColor(colorPorOrden(orden));
        carpeta.setOrden(orden);
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

    @Transactional
    public CarpetaDocumentacion crearCarpetaLibre(String nombre, Long proveedorId, String apodo, String color, Long carpetaPadreId, Obra obra) {
        Proveedor proveedor = proveedorId == null ? null : proveedorService.obtener(proveedorId);
        CarpetaDocumentacion carpetaPadre = carpetaPadreId == null ? null : carpetaRepository.findById(carpetaPadreId)
                .orElseThrow(() -> new EntityNotFoundException("No existe la carpeta padre " + carpetaPadreId));
        if (carpetaPadre != null && (carpetaPadre.getObra() == null || !carpetaPadre.getObra().getId().equals(obra.getId()) || !carpetaPadre.isActivo())) {
            throw new IllegalArgumentException("La carpeta padre no pertenece a la obra activa.");
        }
        String nombreNormalizado = texto(nombre);
        if (!StringUtils.hasText(nombreNormalizado) && proveedor != null) {
            nombreNormalizado = proveedor.getNombre();
        }
        if (!StringUtils.hasText(nombreNormalizado)) {
            throw new IllegalArgumentException("Debe indicar un nombre para la carpeta.");
        }
        if (proveedor != null && carpetaRepository.existsByObraIdAndProveedorIdAndActivoTrue(obra.getId(), proveedor.getId())) {
            throw new IllegalArgumentException("Ese contratista ya tiene carpeta documental.");
        }
        validarNombreCarpetaUnico(obra, StringUtils.hasText(apodo) ? apodo : nombreNormalizado, null);
        int orden = siguienteOrdenCarpeta(obra);
        CarpetaDocumentacion carpeta = new CarpetaDocumentacion();
        carpeta.setObra(obra);
        carpeta.setProveedor(proveedor);
        carpeta.setPadre(carpetaPadre);
        carpeta.setNombre(nombreNormalizado);
        carpeta.setApodo(texto(apodo));
        carpeta.setColor(colorValido(color) ? color.trim() : colorPorOrden(orden));
        carpeta.setOrden(orden);
        carpeta.setGeneral(false);
        return carpetaRepository.save(carpeta);
    }

    @Transactional
    public void personalizarCarpeta(Long carpetaId, String nombre, String apodo, String color, Long proveedorId) {
        CarpetaDocumentacion carpeta = carpetaRepository.findById(carpetaId)
                .orElseThrow(() -> new EntityNotFoundException("No existe la carpeta documental " + carpetaId));
        String nombreNuevo = StringUtils.hasText(nombre) ? nombre.trim() : carpeta.getNombre();
        String apodoNuevo = texto(apodo);
        validarNombreCarpetaUnico(carpeta.getObra(), StringUtils.hasText(apodoNuevo) ? apodoNuevo : nombreNuevo, carpeta.getId());
        if (StringUtils.hasText(nombre)) {
            carpeta.setNombre(nombre.trim());
        }
        carpeta.setApodo(apodoNuevo);
        if (colorValido(color)) {
            carpeta.setColor(color.trim());
        }
        if (!carpeta.isGeneral()) {
            Proveedor proveedor = proveedorId == null ? null : proveedorService.obtener(proveedorId);
            if (proveedor != null) {
                boolean duplicada = carpetasActivas(carpeta.getObra()).stream()
                        .anyMatch(otra -> !otra.getId().equals(carpeta.getId())
                                && otra.getProveedor() != null
                                && otra.getProveedor().getId().equals(proveedor.getId()));
                if (duplicada) {
                    throw new IllegalArgumentException("Ese contratista ya esta vinculado a otra carpeta documental.");
                }
            }
            carpeta.setProveedor(proveedor);
        }
        carpetaRepository.save(carpeta);
    }

    @Transactional
    public void eliminarCarpeta(Long carpetaId) {
        CarpetaDocumentacion carpeta = carpetaRepository.findById(carpetaId)
                .orElseThrow(() -> new EntityNotFoundException("No existe la carpeta documental " + carpetaId));
        if (carpeta.isGeneral()) {
            throw new IllegalArgumentException("La carpeta general de obra no se puede eliminar.");
        }
        carpeta.setActivo(false);
        carpetaRepository.save(carpeta);
    }

    @Transactional
    public void moverCarpeta(Long carpetaId, int direccion, Obra obra) {
        List<CarpetaDocumentacion> carpetas = new java.util.ArrayList<>(carpetasActivas(obra));
        for (int i = 0; i < carpetas.size(); i++) {
            carpetas.get(i).setOrden(i);
        }
        int indice = java.util.stream.IntStream.range(0, carpetas.size())
                .filter(i -> carpetas.get(i).getId().equals(carpetaId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("No existe la carpeta documental " + carpetaId));
        int destino = Math.max(0, Math.min(carpetas.size() - 1, indice + direccion));
        if (indice == destino) {
            return;
        }
        java.util.Collections.swap(carpetas, indice, destino);
        for (int i = 0; i < carpetas.size(); i++) {
            carpetas.get(i).setOrden(i);
        }
        carpetaRepository.saveAll(carpetas);
    }

    @Transactional
    public void moverCarpetas(List<Long> carpetaIds, Long carpetaPadreId, Obra obra) {
        if (carpetaIds == null || carpetaIds.isEmpty()) {
            return;
        }
        CarpetaDocumentacion destino = carpetaPadreId == null ? null : carpetaRepository.findById(carpetaPadreId)
                .orElseThrow(() -> new EntityNotFoundException("No existe la carpeta destino " + carpetaPadreId));
        if (destino != null && (destino.getObra() == null || !destino.getObra().getId().equals(obra.getId()) || !destino.isActivo())) {
            throw new IllegalArgumentException("La carpeta destino no pertenece a la obra activa.");
        }
        List<CarpetaDocumentacion> carpetas = carpetaIds.stream()
                .map(id -> carpetaRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("No existe la carpeta documental " + id)))
                .toList();
        for (CarpetaDocumentacion carpeta : carpetas) {
            if (carpeta.isGeneral()) {
                throw new IllegalArgumentException("La carpeta general de obra no se puede mover.");
            }
            if (carpeta.getObra() == null || !carpeta.getObra().getId().equals(obra.getId())) {
                throw new IllegalArgumentException("Una de las carpetas seleccionadas no pertenece a la obra activa.");
            }
            if (destino != null && carpeta.getId().equals(destino.getId())) {
                throw new IllegalArgumentException("No podes mover una carpeta dentro de si misma.");
            }
            if (destino != null && esDescendiente(destino, carpeta)) {
                throw new IllegalArgumentException("No podes mover una carpeta dentro de una subcarpeta propia.");
            }
            carpeta.setPadre(destino);
            carpeta.setOrden(siguienteOrdenCarpeta(obra));
        }
        carpetaRepository.saveAll(carpetas);
    }

    @Transactional
    public void reordenarCarpetas(List<Long> carpetaIds, Obra obra) {
        if (carpetaIds == null || carpetaIds.isEmpty()) {
            return;
        }
        Map<Long, CarpetaDocumentacion> carpetasPorId = carpetasActivas(obra).stream()
                .collect(java.util.stream.Collectors.toMap(CarpetaDocumentacion::getId, carpeta -> carpeta));
        List<CarpetaDocumentacion> reordenadas = new java.util.ArrayList<>();
        for (Long carpetaId : carpetaIds) {
            CarpetaDocumentacion carpeta = carpetasPorId.remove(carpetaId);
            if (carpeta != null) {
                reordenadas.add(carpeta);
            }
        }
        reordenadas.addAll(carpetasPorId.values());
        for (int i = 0; i < reordenadas.size(); i++) {
            reordenadas.get(i).setOrden(i);
        }
        carpetaRepository.saveAll(reordenadas);
    }

    @Transactional(readOnly = true)
    public DocumentoObra obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el documento " + id));
    }

    @Transactional
    public DocumentoObra guardar(DocumentoObraForm form, Obra obra) {
        CarpetaDocumentacion carpeta = form.getCarpetaId() == null ? null : carpetaActiva(form.getCarpetaId(), obra);
        if (carpeta != null) {
            if (carpeta.getProveedor() != null && form.getProveedorId() == null) {
                form.setProveedorId(carpeta.getProveedor().getId());
                form.setSujeto(SujetoDocumental.CONTRATISTA);
            } else if (form.getProveedorId() == null && form.getSujeto() == SujetoDocumental.CONTRATISTA) {
                form.setSujeto(SujetoDocumental.OBRA);
            }
        }
        if (form.getFechaUltimaVerificacionFisica() == null) {
            form.setFechaUltimaVerificacionFisica(LocalDate.now());
        }
        validar(form);
        DocumentoObra documento = form.getId() == null ? new DocumentoObra() : obtener(form.getId());
        documento.setObra(obra);
        documento.setCarpeta(carpeta);
        documento.setSujeto(form.getSujeto());
        documento.setVinculo(form.getVinculo());
        documento.setTipo(form.getTipo());
        documento.setNombrePersonalizado(texto(form.getNombrePersonalizado()));
        documento.setVehiculoDominio(texto(form.getVehiculoDominio()));
        documento.setVehiculoDetalle(texto(form.getVehiculoDetalle()));
        documento.setFechaEmision(form.getFechaEmision());
        documento.setFechaVencimiento(form.isSinVencimiento() ? null : form.getFechaVencimiento());
        documento.setFechaPresentacion(form.getFechaPresentacion());
        documento.setFechaVencimientoFisico(form.isSinVencimiento() ? null : form.getFechaVencimientoFisico());
        documento.setFechaUltimaVerificacionFisica(form.getFechaUltimaVerificacionFisica());
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
        form.setCarpetaId(documento.getCarpeta() == null ? null : documento.getCarpeta().getId());
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
        form.setSinVencimiento(documento.sinVencimiento());
        form.setFechaPresentacion(documento.getFechaPresentacion());
        form.setFechaVencimientoFisico(documento.getFechaVencimientoFisico());
        form.setFechaUltimaVerificacionFisica(documento.getFechaUltimaVerificacionFisica());
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


    public DocumentacionResumen resumenDesdeGrupos(List<GrupoDocumentacionContratista> grupos) {
        List<DocumentoObra> documentos = grupos == null
                ? List.of()
                : grupos.stream()
                .flatMap(grupo -> grupo.documentos().stream())
                .toList();
        Map<String, MutableContratista> porContratista = new LinkedHashMap<>();
        if (grupos != null) {
            for (GrupoDocumentacionContratista grupo : grupos) {
                MutableContratista resumen = porContratista.computeIfAbsent(grupo.nombre(), key -> new MutableContratista());
                resumen.total = grupo.total();
                resumen.vencidos = grupo.vencidos();
                resumen.porVencer = grupo.porVencer();
                resumen.pendientes = grupo.pendientes();
            }
        }
        DocumentacionResumen base = resumen(documentos, List.of());
        List<ContratistaDocumentacionResumen> contratistas = porContratista.entrySet().stream()
                .map(entry -> entry.getValue().toResumen(entry.getKey()))
                .sorted(Comparator.comparing(ContratistaDocumentacionResumen::vencidos).reversed()
                        .thenComparing(ContratistaDocumentacionResumen::porVencer).reversed())
                .toList();
        return new DocumentacionResumen(
                base.total(),
                base.aptos(),
                base.porVencer(),
                base.vencidos(),
                base.pendientes(),
                base.mensualesPendientes(),
                base.carpetaFisicaPendiente(),
                contratistas);
    }
    public DocumentacionResumen resumen(List<DocumentoObra> documentos) {
        return resumen(documentos, List.of());
    }

    public DocumentacionResumen resumen(List<DocumentoObra> documentos, List<Proveedor> proveedores) {
        long aptos = 0, porVencer = 0, vencidos = 0, pendientes = 0, mensualesPendientes = 0, carpetaFisicaPendiente = 0;
        Map<String, MutableContratista> porContratista = new LinkedHashMap<>();
        LocalDate hoy = LocalDate.now();
        for (DocumentoObra documento : documentos) {
            EstadoDocumentoObra estado = documento.estado();
            if (estado == EstadoDocumentoObra.APTO || estado == EstadoDocumentoObra.SIN_VENCIMIENTO) aptos++;
            if (estado == EstadoDocumentoObra.POR_VENCER) porVencer++;
            if (estado == EstadoDocumentoObra.VENCIDO) vencidos++;
            if (estado == EstadoDocumentoObra.PENDIENTE) pendientes++;
            if (documento.carpetaFisicaPendiente()) carpetaFisicaPendiente++;
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
        return new DocumentacionResumen(documentos.size(), aptos, porVencer, vencidos, pendientes, mensualesPendientes, carpetaFisicaPendiente, contratistas);
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

    @Transactional(readOnly = true)
    public CarpetaDocumentacion obtenerCarpeta(Long carpetaId, Obra obra) {
        return carpetaActiva(carpetaId, obra);
    }

    private CarpetaDocumentacion carpetaActiva(Long carpetaId, Obra obra) {
        CarpetaDocumentacion carpeta = carpetaRepository.findById(carpetaId)
                .orElseThrow(() -> new EntityNotFoundException("No existe la carpeta documental " + carpetaId));
        if (carpeta.getObra() == null || !carpeta.getObra().getId().equals(obra.getId()) || !carpeta.isActivo()) {
            throw new IllegalArgumentException("La carpeta documental no pertenece a la obra activa.");
        }
        return carpeta;
    }

    private void validarNombreCarpetaUnico(Obra obra, String nombreVisible, Long carpetaIdExcluir) {
        String claveNueva = claveCarpeta(nombreVisible);
        if (!StringUtils.hasText(claveNueva)) {
            throw new IllegalArgumentException("Debe indicar un nombre para la carpeta.");
        }
        boolean duplicada = carpetasActivas(obra).stream()
                .filter(carpeta -> carpetaIdExcluir == null || !carpeta.getId().equals(carpetaIdExcluir))
                .map(CarpetaDocumentacion::nombreVisible)
                .map(this::claveCarpeta)
                .anyMatch(claveNueva::equals);
        if (duplicada) {
            throw new IllegalArgumentException("Ya existe una carpeta documental con ese nombre. Cada carpeta debe ser unica.");
        }
    }

    private String claveCarpeta(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "";
        }
        return Normalizer.normalize(valor.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ");
    }
    private boolean colorValido(String color) {
        return StringUtils.hasText(color) && color.trim().matches("^#[0-9a-fA-F]{6}$");
    }

    private boolean esDescendiente(CarpetaDocumentacion posibleDescendiente, CarpetaDocumentacion carpeta) {
        CarpetaDocumentacion cursor = posibleDescendiente;
        int guard = 0;
        while (cursor != null && guard < 100) {
            if (cursor.getId() != null && cursor.getId().equals(carpeta.getId())) {
                return true;
            }
            cursor = cursor.getPadre();
            guard++;
        }
        return false;
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
        return carpetaRepository.findByObraIdAndActivoTrueOrderByOrdenAscGeneralDescNombreAsc(obra.getId());
    }

    private void asegurarCarpetasIniciales(Obra obra) {
        if (carpetaRepository.findByObraIdAndGeneralTrueAndActivoTrue(obra.getId()).isEmpty()) {
            CarpetaDocumentacion general = new CarpetaDocumentacion();
            general.setObra(obra);
            general.setNombre("Simende (Obra)");
            general.setApodo("Obra");
            general.setColor("#3f6f8f");
            general.setOrden(0);
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
                    carpeta.setApodo(nombreBase.equals("El Artesano") ? "El Artesano" : null);
                    carpeta.setOrden(siguienteOrdenCarpeta(obra));
                    carpeta.setColor(colorPorOrden(carpeta.getOrden()));
                    carpeta.setGeneral(false);
                    carpetaRepository.save(carpeta);
                }
            });
        }
        normalizarCarpetas(obra);
        fusionarCarpetasDuplicadas(obra);
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

    private GrupoDocumentacionContratista toGrupo(CarpetaDocumentacion carpeta, Long proveedorId, List<DocumentoObra> documentos, boolean general) {
        long aptos = 0, porVencer = 0, vencidos = 0, pendientes = 0;
        for (DocumentoObra documento : documentos) {
            EstadoDocumentoObra estado = documento.estado();
            if (estado == EstadoDocumentoObra.APTO || estado == EstadoDocumentoObra.SIN_VENCIMIENTO) aptos++;
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
                carpeta.getId(),
                carpeta.getPadre() == null ? null : carpeta.getPadre().getId(),
                proveedorId,
                carpeta.nombreVisible(),
                carpeta.getNombre(),
                carpeta.getApodo(),
                StringUtils.hasText(carpeta.getColor()) ? carpeta.getColor() : "#3f6f8f",
                carpeta.getOrden() == null ? 0 : carpeta.getOrden(),
                estadoGrupo,
                documentos.size(),
                aptos,
                porVencer,
                vencidos,
                pendientes,
                general ? "is-general" : (proveedorId == null ? "is-libre" : cssVinculo(vinculoPrincipal)),
                general ? "General" : (proveedorId == null ? "Carpeta libre" : vinculoPrincipal.getDescripcion()),
                general,
                documentos
        );
    }

    private int siguienteOrdenCarpeta(Obra obra) {
        return carpetasActivas(obra).stream()
                .map(CarpetaDocumentacion::getOrden)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
    }

    private String colorPorOrden(int orden) {
        List<String> colores = List.of("#3f6f8f", "#407761", "#9a6b43", "#9a5f5d", "#74678f", "#3e7f86", "#646f6a");
        return colores.get(Math.floorMod(orden, colores.size()));
    }

    private void normalizarCarpetas(Obra obra) {
        List<CarpetaDocumentacion> carpetas = carpetasActivas(obra);
        boolean guardar = false;
        for (int i = 0; i < carpetas.size(); i++) {
            CarpetaDocumentacion carpeta = carpetas.get(i);
            if (carpeta.getOrden() == null) {
                carpeta.setOrden(i);
                guardar = true;
            }
            if (!StringUtils.hasText(carpeta.getColor())) {
                carpeta.setColor(colorPorOrden(i));
                guardar = true;
            }
        }
        if (guardar) {
            carpetaRepository.saveAll(carpetas);
        }
    }


    private void fusionarCarpetasDuplicadas(Obra obra) {
        List<CarpetaDocumentacion> carpetas = carpetasActivas(obra);
        Map<String, CarpetaDocumentacion> porNombre = new LinkedHashMap<>();
        Map<Long, CarpetaDocumentacion> porProveedor = new LinkedHashMap<>();
        boolean guardar = false;
        for (CarpetaDocumentacion carpeta : carpetas) {
            CarpetaDocumentacion carpetaPrincipal = null;
            if (carpeta.getProveedor() != null) {
                carpetaPrincipal = porProveedor.putIfAbsent(carpeta.getProveedor().getId(), carpeta);
            }
            String clave = claveCarpeta(carpeta.nombreVisible());
            if (carpetaPrincipal == null && StringUtils.hasText(clave)) {
                carpetaPrincipal = porNombre.putIfAbsent(clave, carpeta);
            }
            if (carpetaPrincipal != null && !carpetaPrincipal.getId().equals(carpeta.getId())) {
                reasignarDocumentos(carpeta, carpetaPrincipal, obra);
                carpeta.setActivo(false);
                guardar = true;
            }
        }
        if (guardar) {
            carpetaRepository.saveAll(carpetas);
        }
    }

    private void reasignarDocumentos(CarpetaDocumentacion origen, CarpetaDocumentacion destino, Obra obra) {
        List<DocumentoObra> documentos = listar(obra).stream()
                .filter(documento -> documento.getCarpeta() != null && documento.getCarpeta().getId().equals(origen.getId()))
                .toList();
        for (DocumentoObra documento : documentos) {
            documento.setCarpeta(destino);
            if (documento.getProveedor() == null && destino.getProveedor() != null) {
                documento.setProveedor(destino.getProveedor());
            }
        }
        if (!documentos.isEmpty()) {
            repository.saveAll(documentos);
        }
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

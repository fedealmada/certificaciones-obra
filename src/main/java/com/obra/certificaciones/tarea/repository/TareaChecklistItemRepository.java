package com.obra.certificaciones.tarea.repository;

import com.obra.certificaciones.tarea.entity.TareaChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TareaChecklistItemRepository extends JpaRepository<TareaChecklistItem, Long> {
}

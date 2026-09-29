package br.com.murkyweb.unesc_share.shared.persistence;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BaseEntity {

	@Id
	@GeneratedValue
	@UuidGenerator
	@Column(nullable = false, updatable = false)
	private UUID id;
}

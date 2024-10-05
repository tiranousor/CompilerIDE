package com.example.CompilerIDE.mapper;

import com.example.CompilerIDE.Dto.ClientDto;
import com.example.CompilerIDE.providers.Client;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.boot.autoconfigure.integration.IntegrationProperties;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ClientMapper {

    Client updateUserFromDto(ClientDto clientDto, @MappingTarget Client client);
    ClientDto updateUserFromClient(Client client, @MappingTarget ClientDto clientDto);

}

package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.AbilityTagSaveDto;
import org.example.hragent.dto.AbilityTagUpdateDto;
import org.example.hragent.entity.TAbilityTag;
import org.example.hragent.vo.AbilityTagVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-14T10:43:47+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class AbilityTagConverterImpl implements AbilityTagConverter {

    @Override
    public TAbilityTag saveDtoToEntity(AbilityTagSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        TAbilityTag tAbilityTag = new TAbilityTag();

        tAbilityTag.setTagName( dto.getTagName() );
        tAbilityTag.setTagCode( dto.getTagCode() );
        tAbilityTag.setTagCategory( dto.getTagCategory() );
        tAbilityTag.setSort( dto.getSort() );
        tAbilityTag.setStatus( dto.getStatus() );

        return tAbilityTag;
    }

    @Override
    public void updateDtoToEntity(AbilityTagUpdateDto dto, TAbilityTag entity) {
        if ( dto == null ) {
            return;
        }

        entity.setId( dto.getId() );
        entity.setTagName( dto.getTagName() );
        entity.setTagCode( dto.getTagCode() );
        entity.setTagCategory( dto.getTagCategory() );
        entity.setSort( dto.getSort() );
        entity.setStatus( dto.getStatus() );
    }

    @Override
    public AbilityTagVO entityToVo(TAbilityTag entity) {
        if ( entity == null ) {
            return null;
        }

        AbilityTagVO abilityTagVO = new AbilityTagVO();

        abilityTagVO.setId( entity.getId() );
        abilityTagVO.setTagName( entity.getTagName() );
        abilityTagVO.setTagCode( entity.getTagCode() );
        abilityTagVO.setTagCategory( entity.getTagCategory() );
        abilityTagVO.setSort( entity.getSort() );
        abilityTagVO.setStatus( entity.getStatus() );

        return abilityTagVO;
    }
}

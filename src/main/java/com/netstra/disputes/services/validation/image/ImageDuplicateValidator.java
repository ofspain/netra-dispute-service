package com.netstra.disputes.services.validation.image;

import lombok.Data;
import utilities.HashUtil;

import java.io.File;
import java.io.IOException;

@Data
public class ImageDuplicateValidator implements ImageValidator{

    private String ahash;
    private String pHash;

    @Override
    public void validateImage(File file){
        try {
                String  aHashed = HashUtil.computeAverageHash(file);
                String pHashed = HashUtil.computePerpetualHash(file);
                setAhash(aHashed);
                setPHash(pHashed);

                //todo: compare with stored hashes here
        }catch (IOException exception){
            //todo: rethrow app specific exception and handles appropriately
            exception.printStackTrace();
        }
    }
}

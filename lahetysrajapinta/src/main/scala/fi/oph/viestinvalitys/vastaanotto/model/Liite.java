package fi.oph.viestinvalitys.vastaanotto.model;

import java.util.Set;

public interface Liite {

  public static final int TIEDOSTONIMI_MAX_PITUUS           = 255;
  public static final int SISALTOTYYPPI_MAX_PITUUS          = 255;
  public static final Set<String> KIELLETYT_TIEDOSTOTYYPIT  = Set.of(".ade",".adp",".app",".asp",".bas",".bat",".cer",
      ".chm",".cmd",".com",".cpl",".crt",".csh", ".der",".exe",".fxp",".gadget",".hlp",".hta",".inf",".ins",".isp",
      ".its",".js",".jse",".ksh",".lib",".lnk",".mad",".maf",".mag",".mam",".maq", ".mar",".mas",".mat",".mau",".mav",
      ".maw",".mda",".mdb",".mde",".mdt",".mdw",".mdz",".msc",".msh",".msh1",".msh2",".mshxml",".msh1xml",".msh2xml",
      ".msi",".msp",".mst",".ops",".pcd",".pif",".plg",".prf",".prg",".reg",".scf",".scr",".sct",".shb",".shs",".sys",
      ".ps1",".ps1xml",".ps2",".ps2xml", ".psc1",".psc2",".tmp",".url",".vb",".vbe",".vbs",".vps",".vsmacros",".vss",
      ".vst",".vsw",".vxd",".ws",".wsc",".wsf",".wsh",".xnk");

  String getTiedostoNimi();

  String getSisaltoTyyppi();

  byte[] getBytes();

  public static TiedostoNimiBuilder builder() {
    return new LiiteBuilderImpl();
  }

  interface TiedostoNimiBuilder {
    BytesBuilder withFileName(String fileName);
  }

  interface BytesBuilder {
    LiiteBuilder withBytes(byte[] bytes);
  }

  interface LiiteBuilder {
    LiiteBuilder withContentType(String contentType);
    Liite build() throws BuilderException;
  }
}

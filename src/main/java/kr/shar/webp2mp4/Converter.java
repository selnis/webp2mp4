package kr.shar.webp2mp4;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class Converter {

    private static String sBinPath = "";
    private static String sPattern = null;
    private static Integer iRepeat = null;
    private static Boolean isDeleteTmpFiles = true;

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("java kr.shar.webp2mp4.Converter -s webp/*.webp -p pattern -r repeat -b bin/ -d false");
                System.out.println("\t -s : Source file(s). (e.g. webp/001.webp, webp/*.webp)");
                System.out.println("\t -p : (optional) If patterned target file exists, increase repeat count.");
                System.out.println("\t -r : (optional) Set repeat count if target exists.");
                System.out.println("\t -b : (optional) ffmpeg and webp binaries' path.");
                System.out.println("\t -d : (optional) Delete temporary files after job finished.");
                System.exit(0);
            }

            List<CmdParameter> list = getParameters(args);
            System.out.println(list);

            List<File> srcFile = new ArrayList<>();
            for (CmdParameter c : list) {
                switch (c.getKey()) {
                    case "s": {
                        for (String s : c.getValue()) {
                            if (s.endsWith("*.webp")) {
                                srcFile.add(new File(s.replace("*.webp", ".")));
                            } else {
                                srcFile.add(new File(s));
                            }
                        }
                        break;
                    }
                    case "p": {
                        sPattern = c.getValue().get(0);
                        break;
                    }
                    case "r": {
                        iRepeat = Integer.valueOf(c.getValue().get(0));
                        break;
                    }
                    case "b": {
                        sBinPath = c.getValue().get(0);
                        if (!sBinPath.endsWith("\\") && !sBinPath.endsWith("/")) {
                            sBinPath += File.separator;
                        }
                        break;
                    }
                    case "d": {
                        isDeleteTmpFiles = Boolean.valueOf(c.getValue().get(0));
                        break;
                    }
                }
            }

            for (File f : srcFile) {
                if (f.isDirectory()) {
                    for (File fi : f.listFiles()) {
                        processWebp(fi);
                    }
                } else {
                    processWebp(f);
                }
            }

            System.exit(0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void processWebp(File srcFile) throws Exception {
        if (srcFile.getName().endsWith(".webp")) {
            String sPath = srcFile.getAbsolutePath();
            String sFileName = sPath.substring(sPath.lastIndexOf(File.separator) + 1, sPath.lastIndexOf("."));
            String sFileExt = sPath.substring(sPath.lastIndexOf(".") + 1);

            String sBasePath = srcFile.getParentFile().getAbsolutePath();
            if (sBasePath.endsWith(".")) sBasePath = sBasePath.substring(0, sBasePath.length() - 1);
            if (!sBasePath.endsWith("\\")) sBasePath += "\\";

            String sDest = srcFile.getName().replace(".webp", ".mp4");
            String sTmpDir = "tmp_" + sFileName;

            File fTmpDir = new File(sBasePath + sTmpDir);
            if (!fTmpDir.exists()) fTmpDir.mkdirs();

            webp2mp4(sBasePath, srcFile.getName(), sDest, sTmpDir);

            if (isDeleteTmpFiles) {
                for (File f : fTmpDir.listFiles()) {
                    f.delete();
                }
                fTmpDir.delete();
            }
        }
    }


    public static void webp2mp4(String sBasePath, String sSrc, String sDest, String sTmpDir) throws Exception {
        int width = 0;
        int height = 0;
        List<Integer> durations = new ArrayList<>();

        // webpmux
        String cmd = String.format("%swebpmux -info %s", sBinPath, sBasePath + sSrc);
        String res = simpleExec(cmd);

        for (String line : res.split("\\r?\\n")) {
            /*
            $output ==>
            Canvas size: 592 x 418
            Features present: animation EXIF metadata
            Background color : 0xFFFFFFFF  Loop Count : 0
            Number of frames: 5
            No.: width height alpha x_offset y_offset duration   dispose blend image_size  compression
              1:   592   418    no        0        0     1000 background    no       1224       lossy
              2:   592   418    no        0        0     2000 background    no       1904       lossy
              3:   592   418    no        0        0     3000 background    no       2380       lossy
              4:   592   418    no        0        0     4000 background    no       2392       lossy
              5:   592   418    no        0        0     5000 background    no       2968       lossy
            Size of the EXIF metadata: 97
            */
            width = 0;
            // 1:  1136   640    no        0        0       40       none    no     247218    lossless
            line = trimWhitespace(line);

            if (isNumeric(line.substring(0,1))) {
                String[] tmp = line.split(" ");
                durations.add(Integer.parseInt(tmp[6]));

                width = Integer.parseInt(tmp[1]);
                height = Integer.parseInt(tmp[2]);
            }
        }

        // webp2png
        cmd = String.format("%sanim_dump -folder %s %s", sBinPath, sBasePath + sTmpDir, sBasePath + sSrc);
        res = simpleExec(cmd);

        // create concat file
        String concatFileName = writeConcatfile(sSrc, sBasePath + sTmpDir, durations);
        String ffmpegOptions = "-y -pix_fmt yuv420p ";

        // width must be divisible by 2
        if(width % 2 != 0 || height % 2 != 0) {
            ffmpegOptions = ffmpegOptions + String.format("-vf scale=%d:%d ", width - width % 2, height - height % 2);
        }

        // png2mp4
        cmd = String.format("%sffmpeg -f concat -i %s %s %s", sBinPath, concatFileName, ffmpegOptions, sBasePath + sDest);
        res = simpleExec(cmd);
    }

    public static String writeConcatfile(String sSrc, String sTmpDir, List<Integer> durations) throws Exception {
        String concatFileName = sTmpDir + File.separator + "concatfile.txt";

        /*
        concatfile.txt ==>
            file dump_0000.png
            duration 0.1
            file dump_0001.png
            duration 0.2
            ....
        */

        String concatInfo = buildConcatContents(durations).toString();
        if (sPattern != null) {
            if (sSrc.endsWith(".webp")) {
                sSrc = sSrc.substring(0, sSrc.indexOf(".webp"));
            }

            if (Pattern.matches(sPattern, sSrc) ||  sSrc.contains(sPattern)) {
                if (iRepeat != null) {
                    StringBuilder tmp = new StringBuilder();
                    for (int i = 0; i < iRepeat; i++) {
                        tmp.append(concatInfo);
                    }
                    concatInfo = tmp.toString();
                }
            }
        }

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(concatFileName));
            writer.write(concatInfo);
            writer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        return concatFileName;
    }

    public static StringBuilder buildConcatContents(List<Integer> durations) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (int i=0; i<durations.size(); i++) {
            String pngFilename = String.format("dump_%04d.png", i);

            builder.append(String.format("file %s\n", pngFilename));
            builder.append(String.format("duration %f\n", durations.get(i) / 1000.));
        }

        return builder;
    }

    public static String simpleExec(String cmd) {
        StringBuilder result = new StringBuilder();

        System.out.println(cmd);

        try {
            Runtime rt = Runtime.getRuntime();
            Process pr = rt.exec(cmd);

            OutputStream stdin = pr.getOutputStream();
            InputStream stderr = pr.getErrorStream();
            InputStream stdout = pr.getInputStream();

            AtomicReference<Boolean> isFinish = new AtomicReference<>(false);

            //쓰레드 풀을 이용해서 3개의 stream을 대기시킨다.

            //출력 stream을 BufferedReader로 받아서 라인 변경이 있을 경우 console 화면에 출력시킨다.
            Executors.newCachedThreadPool().execute(() -> {
                // 문자 깨짐이 발생할 경우 InputStreamReader(stdout)에 인코딩 타입을 넣는다. ex) InputStreamReader(stdout, "euc-kr")
                // try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, "euc-kr"))) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                        result.append(line);
                        result.append("\n");
                    }

                    isFinish.set(true);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });

            //에러 stream을 BufferedReader로 받아서 에러가 발생할 경우 console 화면에 출력시킨다.
            Executors.newCachedThreadPool().execute(() -> {
                // 문자 깨짐이 발생할 경우 InputStreamReader(stdout)에 인코딩 타입을 넣는다. ex) InputStreamReader(stdout, "euc-kr")
                // try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderr, "euc-kr"))) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderr))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });

            /*
            //입력 stream을 BufferedWriter로 받아서 콘솔로부터 받은 입력을 Process 클래스로 실행시킨다.
            Executors.newCachedThreadPool().execute(() -> {
                // Scanner 클래스는 콘솔로 부터 입력을 받기 위한 클래스 입니다.
                try (Scanner scan = new Scanner(System.in)) {
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin))) {
                        while (true) {
                            // 콘솔로 부터 엔터가 포함되면 input String 변수로 값이 입력됩니다.
                            String input = scan.nextLine();
                            // 콘솔에서 \n가 포함되어야 실행된다.(엔터의 의미인듯 싶습니다.)
                            input += "\n";
                            writer.write(input);
                            // Process로 명령어 입력
                            writer.flush();
                            // exit 명령어가 들어올 경우에는 프로그램을 종료합니다.
                            if ("exit\n".equals(input)) {
                                System.exit(0);
                            }
                        }
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            });
            */

            while (!isFinish.get()) {
                Thread.sleep(100);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return result.toString();
    }

    public static String trimWhitespace(String src) {
        if (src == null) {
            return src;
        } else {
            StringBuffer buffer = new StringBuffer();

            for(char c : src.toCharArray()) {
                if (c == ' ') {
                    if (!buffer.toString().endsWith(" ")) {
                        buffer.append(c);
                    }
                }
                else if (c != '\n' && c != '\f' && c != '\r' && c != '\t') {
                    buffer.append(c);
                }

            }

            return buffer.toString().trim();
        }
    }

    public static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
        } catch (Exception ex) {
            return false;
        }

        return true;
    }

    public static List<CmdParameter> getParameters(final String[] args) {
        List<CmdParameter> list = new ArrayList<>();

        CmdParameter param = null;

        int i=0;
        while (i < args.length) {

            if (args[i].charAt(0) == '-') {
                if (param != null) {
                    list.add(param);
                }
                param = new CmdParameter();
                param.setKey(args[i].substring(1));
                i++;
            } else {
                if (param == null) {
                    throw new IllegalArgumentException("Invalid parameter: " + args[i]);
                }

                param.addValue(args[i]);
                i++;

                if (i == args.length) {
                    list.add(param);
                }
            }

            /*
            if (args[i].charAt(0) != '-' && args[i].charAt(0) != '/') {
                throw new IllegalArgumentException("Invalid parameter: " + args[i]);
            }
            int pos = optstring.indexOf(args[i].charAt(1));
            if (pos == -1) {
                throw new IllegalArgumentException("Invalid parameter: " + args[i]);
            }
            CmdParameter c = new CmdParameter();
            c.setKey(String.valueOf(args[i].charAt(1)));
            list.add(c);
            if (pos < optstring.length() - 1 && optstring.charAt(1 + pos) == ':') {
                ++i;
                if (args.length <= i) {
                    c.setSkip(true);
                }
                c.setValue(args[i]);
            }
            */
        }
        return list;
    }

}
